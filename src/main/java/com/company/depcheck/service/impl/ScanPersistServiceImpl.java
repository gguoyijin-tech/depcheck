package com.company.depcheck.service.impl;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.company.depcheck.engine.report.DependencyJson;
import com.company.depcheck.engine.report.ReportJson;
import com.company.depcheck.engine.report.VulnerabilityJson;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.Dependency;
import com.company.depcheck.model.entity.ScanTask;
import com.company.depcheck.model.entity.Vulnerability;
import com.company.depcheck.service.ScanPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 扫描结果入库实现：任务 → 依赖 → 漏洞，全部在一个事务里。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Service
public class ScanPersistServiceImpl implements ScanPersistService {

    private static final Logger LOG = LoggerFactory.getLogger(ScanPersistServiceImpl.class);

    /** 批量插入每批条数。 */
    private static final int BATCH_SIZE = 500;

    /** 任务号长度。 */
    private static final int TASK_NO_LENGTH = 16;

    private final ScanTaskMapper scanTaskMapper;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper 任务 Mapper
     */
    public ScanPersistServiceImpl(ScanTaskMapper scanTaskMapper) {
        this.scanTaskMapper = scanTaskMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long persist(Long projectId, ReportJson report, long durationMs) {
        LocalDateTime now = LocalDateTime.now();

        Long taskId = saveTask(projectId, report, durationMs, now);
        List<DependencyJson> deps = report.getDependencies() == null ? List.of() : report.getDependencies();

        int depCount = saveDependencies(taskId, deps, now);
        int vulnCount = saveVulnerabilities(taskId, deps, now);

        LOG.info("入库完成 taskId={} deps={} vulns={}", taskId, depCount, vulnCount);
        return taskId;
    }

    /**
     * 写任务记录。D8 会改成 QUEUED→RUNNING→SUCCESS 的状态机。
     */
    private Long saveTask(Long projectId, ReportJson report, long durationMs, LocalDateTime now) {
        ScanTask task = new ScanTask();
        task.setTaskNo(UUID.randomUUID().toString().replace("-", "").substring(0, TASK_NO_LENGTH));
        task.setProjectId(projectId);
        task.setStatus("SUCCESS");
        task.setProgress(100);
        task.setScanMode("BUILD");
        task.setEngineVersion(report.getScanInfo() == null ? null : report.getScanInfo().getEngineVersion());
        task.setStartedAt(now.minusNanos(durationMs * 1_000_000L));
        task.setFinishedAt(now);
        task.setDurationMs(durationMs);
        task.setCreatedAt(now);
        scanTaskMapper.insert(task);
        return task.getId();
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
