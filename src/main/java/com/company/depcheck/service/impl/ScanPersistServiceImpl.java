package com.company.depcheck.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.company.depcheck.common.constant.TaskStage;
import com.company.depcheck.common.constant.TaskStatus;
import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.engine.report.DependencyJson;
import com.company.depcheck.engine.report.ReportJson;
import com.company.depcheck.engine.report.VulnerabilityJson;
import com.company.depcheck.mapper.DependencyMapper;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.mapper.VulnerabilityMapper;
import com.company.depcheck.model.entity.Dependency;
import com.company.depcheck.model.entity.ScanTask;
import com.company.depcheck.model.entity.Vulnerability;
import com.company.depcheck.service.ScanPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描结果入库实现：任务状态 → 依赖 → 漏洞，全部在一个事务里。
 *
 * <p>任务状态用"带前置状态的条件更新"（RUNNING → SUCCESS）落库：
 * 如果看门狗已经把任务置为 FAILED，这里的更新影响 0 行，整个事务回滚，
 * 明细不会挂到一个已失败的任务上。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Service
public class ScanPersistServiceImpl implements ScanPersistService {

    private static final Logger LOG = LoggerFactory.getLogger(ScanPersistServiceImpl.class);

    /** 批量插入每批条数。 */
    private static final int BATCH_SIZE = 500;

    /** 成功时的进度。 */
    private static final int PROGRESS_DONE = 100;

    private final ScanTaskMapper scanTaskMapper;

    private final DependencyMapper dependencyMapper;

    private final VulnerabilityMapper vulnerabilityMapper;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper      任务 Mapper
     * @param dependencyMapper    依赖 Mapper（幂等重入时先清旧明细）
     * @param vulnerabilityMapper 漏洞 Mapper
     */
    public ScanPersistServiceImpl(ScanTaskMapper scanTaskMapper, DependencyMapper dependencyMapper,
                                  VulnerabilityMapper vulnerabilityMapper) {
        this.scanTaskMapper = scanTaskMapper;
        this.dependencyMapper = dependencyMapper;
        this.vulnerabilityMapper = vulnerabilityMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int persist(Long taskId, ReportJson report) {
        LocalDateTime now = LocalDateTime.now();
        List<DependencyJson> deps = report.getDependencies() == null ? List.of() : report.getDependencies();

        // 幂等：同一任务重复入库时先清旧明细（唯一索引 uk_dep_task_file / uk_vuln_task_cve 兜底）
        clearDetails(taskId);

        int depCount = saveDependencies(taskId, deps, now);
        int vulnCount = saveVulnerabilities(taskId, deps, now);

        finishTask(taskId, report, now);

        LOG.info("入库完成 taskId={} deps={} vulns={}", taskId, depCount, vulnCount);
        return depCount;
    }

    /**
     * 清掉该任务已有的明细（先漏洞后依赖，与级联方向一致）。
     *
     * @param taskId 任务 ID
     */
    private void clearDetails(Long taskId) {
        vulnerabilityMapper.delete(new LambdaQueryWrapper<Vulnerability>()
                .eq(Vulnerability::getTaskId, taskId));
        dependencyMapper.delete(new LambdaQueryWrapper<Dependency>()
                .eq(Dependency::getTaskId, taskId));
    }

    /**
     * 把任务从 RUNNING 置为 SUCCESS。
     *
     * <p>条件更新 {@code where status = 'RUNNING'}：任务若已被取消/超时改写过状态，
     * 这里影响 0 行，直接抛异常让事务回滚，避免"任务失败但明细入库"的脏数据。</p>
     *
     * @param taskId 任务 ID
     * @param report 解析后的报告
     * @param now    当前时间
     */
    private void finishTask(Long taskId, ReportJson report, LocalDateTime now) {
        ScanTask old = scanTaskMapper.selectById(taskId);
        if (old == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "任务不存在：" + taskId);
        }

        ScanTask update = new ScanTask();
        update.setStatus(TaskStatus.SUCCESS);
        update.setProgress(PROGRESS_DONE);
        update.setStage(TaskStage.DONE);
        update.setFinishedAt(now);
        update.setEngineVersion(report.getScanInfo() == null ? null : report.getScanInfo().getEngineVersion());
        if (old.getStartedAt() != null) {
            update.setDurationMs(Duration.between(old.getStartedAt(), now).toMillis());
        }

        int rows = scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, taskId)
                .eq(ScanTask::getStatus, TaskStatus.RUNNING));
        if (rows == 0) {
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(),
                    "任务状态已变更（可能被取消或超时），本次结果丢弃：taskId=" + taskId);
        }
    }

    /**
     * 批量写依赖，返回条数。
     */
    private int saveDependencies(Long taskId, List<DependencyJson> deps, LocalDateTime now) {
        if (deps.isEmpty()) {
            return 0;
        }
        List<Dependency> rows = new ArrayList<>(deps.size());
        for (DependencyJson d : deps) {
            Dependency row = new Dependency();
            row.setTaskId(taskId);
            row.setFilePath(truncate(d.getFilePath(), 1000));
            row.setPurl(truncate(d.getPackages() == null || d.getPackages().isEmpty()
                    ? null : d.getPackages().get(0).getId(), 500));
            fillNameAndVersion(row, d);
            row.setLicenseName(truncate(d.getLicense(), 250));
            row.setLicenseRisk(judgeLicenseRisk(d.getLicense()));
            row.setCreatedAt(now);
            rows.add(row);
        }
        Db.saveBatch(rows, BATCH_SIZE);
        return rows.size();
    }

    /**
     * 批量写漏洞明细。
     *
     * <p>{@code saveBatch} 不回填自增主键，所以先按 task_id 回查依赖，建立 filePath → id 映射。</p>
     */
    private int saveVulnerabilities(Long taskId, List<DependencyJson> deps, LocalDateTime now) {
        Map<String, Long> depIdByFile = new HashMap<>(deps.size() * 2);
        if (!deps.isEmpty()) {
            List<Dependency> saved = Db.lambdaQuery(Dependency.class)
                    .eq(Dependency::getTaskId, taskId).list();
            for (Dependency d : saved) {
                depIdByFile.put(d.getFilePath(), d.getId());
            }
        }

        List<Vulnerability> rows = new ArrayList<>();
        for (DependencyJson d : deps) {
            if (d.getVulnerabilities() == null) {
                continue;
            }
            Long depId = depIdByFile.get(truncate(d.getFilePath(), 1000));
            for (VulnerabilityJson v : d.getVulnerabilities()) {
                Vulnerability row = new Vulnerability();
                row.setTaskId(taskId);
                row.setDependencyId(depId);
                row.setCveId(truncate(v.getName(), 60));
                row.setCvssScore(pickScore(v));
                row.setSeverity(v.getSeverity());
                row.setTitle(truncate(v.getName(), 500));
                row.setDescription(truncate(v.getDescription(), 3900));
                row.setCwe(v.getCwes() == null || v.getCwes().isEmpty() ? null : v.getCwes().get(0));
                row.setRefUrl(truncate(v.getReferences() == null || v.getReferences().isEmpty()
                        ? null : v.getReferences().get(0).getUrl(), 990));
                row.setSuppressed(0);
                row.setCreatedAt(now);
                rows.add(row);
            }
        }
        if (!rows.isEmpty()) {
            Db.saveBatch(rows, BATCH_SIZE);
        }
        return rows.size();
    }

    /**
     * 从 purl 或文件名里取 name/version/ecosystem。
     */
    private void fillNameAndVersion(Dependency row, DependencyJson d) {
        String purl = row.getPurl();
        if (purl != null && purl.startsWith("pkg:")) {
            String body = purl.substring(4);
            int slash = body.indexOf('/');
            row.setEcosystem(slash > 0 ? body.substring(0, slash) : null);
            String rest = slash > 0 ? body.substring(slash + 1) : body;
            int at = rest.lastIndexOf('@');
            if (at > 0) {
                row.setName(truncate(rest.substring(0, at), 250));
                row.setVersion(truncate(rest.substring(at + 1), 120));
                return;
            }
            row.setName(truncate(rest, 250));
            return;
        }
        String fileName = d.getFileName() == null ? "" : d.getFileName();
        int colon = fileName.lastIndexOf(": ");
        String baseName = colon >= 0 ? fileName.substring(colon + 2) : fileName;
        row.setName(truncate(baseName, 250));
    }

    /**
     * CVSS 取值：v3 → v4 → null。
     */
    private BigDecimal pickScore(VulnerabilityJson v) {
        if (v.getCvssv3() != null && v.getCvssv3().getBaseScore() != null) {
            return v.getCvssv3().getBaseScore();
        }
        if (v.getCvssv4() != null && v.getCvssv4().getBaseScore() != null) {
            return v.getCvssv4().getBaseScore();
        }
        return null;
    }

    /**
     * 许可证四档：LOW / MEDIUM / HIGH / UNKNOWN。
     */
    private String judgeLicenseRisk(String license) {
        if (license == null || license.isBlank()) {
            return "UNKNOWN";
        }
        String l = license.toLowerCase();
        if (l.contains("agpl") || l.contains("gpl") || l.contains("sspl")) {
            return "HIGH";
        }
        if (l.contains("lgpl") || l.contains("mpl") || l.contains("epl")) {
            return "MEDIUM";
        }
        if (l.contains("apache") || l.contains("mit") || l.contains("bsd") || l.contains("isc")) {
            return "LOW";
        }
        return "UNKNOWN";
    }

    /**
     * 超长截断，防止列溢出导致整批失败。
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
