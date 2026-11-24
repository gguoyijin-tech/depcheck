package com.company.depcheck.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.config.ScanProperties;
import com.company.depcheck.mapper.DependencyMapper;
import com.company.depcheck.mapper.VulnerabilityMapper;
import com.company.depcheck.model.entity.Dependency;
import com.company.depcheck.model.entity.ScanTask;
import com.company.depcheck.model.entity.Vulnerability;
import com.company.depcheck.model.vo.DependencyVO;
import com.company.depcheck.model.vo.PageResult;
import com.company.depcheck.model.vo.ScanSummaryVO;
import com.company.depcheck.model.vo.VulnerabilityVO;
import com.company.depcheck.service.ScanTaskQueryService;
import com.company.depcheck.service.support.TaskAccessGuard;

/**
 * 扫描结果查询实现。
 *
 * <p>三个接口都要先过 {@link TaskAccessGuard}（不是自己的任务按不存在处理），
 * 再按 {@code task_id} 索引查明细；分页上限由 MP 分页插件兜住（200 条/页）。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Service
public class ScanTaskQueryServiceImpl implements ScanTaskQueryService {

    /** 默认每页条数。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页上限（与 PaginationInnerInterceptor.maxLimit 保持一致）。 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 严重度取值顺序（概览卡展示用）。 */
    private static final String[] SEVERITIES = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"};

    /** 许可证风险取值顺序。 */
    private static final String[] LICENSE_RISKS = {"HIGH", "MEDIUM", "LOW", "UNKNOWN"};

    /** 引擎产出的 HTML 报告文件名。 */
    private static final String REPORT_FILE_NAME = "dependency-check-report.html";

    private final DependencyMapper dependencyMapper;

    private final VulnerabilityMapper vulnerabilityMapper;

    private final TaskAccessGuard taskAccessGuard;

    private final ScanProperties scanProperties;

    /**
     * 构造注入。
     *
     * @param dependencyMapper    依赖 Mapper
     * @param vulnerabilityMapper 漏洞 Mapper
     * @param taskAccessGuard     任务归属校验
     * @param scanProperties      扫描配置（报告根目录）
     */
    public ScanTaskQueryServiceImpl(DependencyMapper dependencyMapper, VulnerabilityMapper vulnerabilityMapper,
                                    TaskAccessGuard taskAccessGuard, ScanProperties scanProperties) {
        this.dependencyMapper = dependencyMapper;
        this.vulnerabilityMapper = vulnerabilityMapper;
        this.taskAccessGuard = taskAccessGuard;
        this.scanProperties = scanProperties;
    }

    @Override
    public Path reportFile(Long taskId, String sessionId) {
        ScanTask task = taskAccessGuard.loadOwned(taskId, sessionId);
        Path file = Path.of(scanProperties.getReportRoot(), task.getTaskNo(), REPORT_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "报告不存在或已被清理，请重新扫描");
        }
        return file;
    }

    @Override
    public ScanSummaryVO summary(Long taskId, String sessionId) {
        ScanTask task = taskAccessGuard.loadOwned(taskId, sessionId);

        ScanSummaryVO vo = new ScanSummaryVO();
        vo.setTaskId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setStatus(task.getStatus());
        vo.setDependencyCount(dependencyMapper.selectCount(new LambdaQueryWrapper<Dependency>()
                .eq(Dependency::getTaskId, taskId)));
        vo.setVulnerabilityCount(vulnerabilityMapper.selectCount(new LambdaQueryWrapper<Vulnerability>()
                .eq(Vulnerability::getTaskId, taskId)));
        vo.setSeverityCounts(fill(severityCounts(taskId), SEVERITIES));
        vo.setLicenseRiskCounts(fill(licenseRiskCounts(taskId), LICENSE_RISKS));
        return vo;
    }

    @Override
    public PageResult<DependencyVO> listDependencies(Long taskId, String sessionId,
                                                     int page, int size, String keyword, String licenseRisk) {
        taskAccessGuard.loadOwned(taskId, sessionId);
        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);

        LambdaQueryWrapper<Dependency> wrapper = new LambdaQueryWrapper<Dependency>()
                .eq(Dependency::getTaskId, taskId)
                .orderByAsc(Dependency::getName)
                .orderByAsc(Dependency::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Dependency::getName, kw).or().like(Dependency::getPurl, kw));
        }
        if (StringUtils.hasText(licenseRisk)) {
            wrapper.eq(Dependency::getLicenseRisk, licenseRisk.trim().toUpperCase(Locale.ROOT));
        }

        IPage<Dependency> result = dependencyMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<Dependency> rows = result.getRecords();
        Map<Long, Long> vulnCounts = vulnerabilityCounts(taskId, rows);

        List<DependencyVO> records = new ArrayList<>(rows.size());
        for (Dependency row : rows) {
            DependencyVO vo = new DependencyVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setVersion(row.getVersion());
            vo.setEcosystem(row.getEcosystem());
            vo.setPurl(row.getPurl());
            vo.setFilePath(row.getFilePath());
            vo.setLicenseName(row.getLicenseName());
            vo.setLicenseRisk(row.getLicenseRisk());
            vo.setVulnerabilityCount(vulnCounts.getOrDefault(row.getId(), 0L));
            records.add(vo);
        }
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public PageResult<VulnerabilityVO> listVulnerabilities(Long taskId, String sessionId,
                                                           int page, int size, String severity) {
        taskAccessGuard.loadOwned(taskId, sessionId);
        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);

        LambdaQueryWrapper<Vulnerability> wrapper = new LambdaQueryWrapper<Vulnerability>()
                .eq(Vulnerability::getTaskId, taskId);
        if (StringUtils.hasText(severity)) {
            wrapper.eq(Vulnerability::getSeverity, severity.trim().toUpperCase(Locale.ROOT));
        }
        // H2 默认把 NULL 当最大值，DESC 会把无评分漏洞排在最前，这里显式 NULLS LAST
        wrapper.last("ORDER BY (CVSS_SCORE IS NULL) ASC, CVSS_SCORE DESC, ID ASC");

        IPage<Vulnerability> result = vulnerabilityMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<Vulnerability> rows = result.getRecords();
        Map<Long, Dependency> depById = dependenciesById(rows);

        List<VulnerabilityVO> records = new ArrayList<>(rows.size());
        for (Vulnerability row : rows) {
            VulnerabilityVO vo = new VulnerabilityVO();
            vo.setId(row.getId());
            vo.setCveId(row.getCveId());
            vo.setCvssScore(row.getCvssScore());
            vo.setSeverity(row.getSeverity());
            vo.setTitle(row.getTitle());
            vo.setCwe(row.getCwe());
            vo.setRefUrl(row.getRefUrl());
            Dependency dep = row.getDependencyId() == null ? null : depById.get(row.getDependencyId());
            if (dep != null) {
                vo.setDependencyName(dep.getName());
                vo.setDependencyVersion(dep.getVersion());
            }
            records.add(vo);
        }
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 严重度分布。 */
    private Map<String, Long> severityCounts(Long taskId) {
        QueryWrapper<Vulnerability> wrapper = new QueryWrapper<>();
        wrapper.select("severity", "COUNT(*) AS cnt")
                .eq("task_id", taskId)
                .groupBy("severity");
        return toCountMap(vulnerabilityMapper.selectMaps(wrapper), "severity", "cnt");
    }

    /** 许可证风险分布。 */
    private Map<String, Long> licenseRiskCounts(Long taskId) {
        QueryWrapper<Dependency> wrapper = new QueryWrapper<>();
        wrapper.select("license_risk", "COUNT(*) AS cnt")
                .eq("task_id", taskId)
                .groupBy("license_risk");
        return toCountMap(dependencyMapper.selectMaps(wrapper), "license_risk", "cnt");
    }

    /** 当前页依赖各自命中多少漏洞。 */
    private Map<Long, Long> vulnerabilityCounts(Long taskId, List<Dependency> rows) {
        Map<Long, Long> counts = new HashMap<>(rows.size() * 2);
        if (rows.isEmpty()) {
            return counts;
        }
        List<Long> ids = rows.stream().map(Dependency::getId).toList();
        QueryWrapper<Vulnerability> wrapper = new QueryWrapper<>();
        wrapper.select("dependency_id", "COUNT(*) AS cnt")
                .eq("task_id", taskId)
                .in("dependency_id", ids)
                .groupBy("dependency_id");
        for (Map<String, Object> row : vulnerabilityMapper.selectMaps(wrapper)) {
            Map<String, Object> lower = lowerKeys(row);
            Object depId = lower.get("dependency_id");
            Object cnt = lower.get("cnt");
            if (depId instanceof Number id && cnt instanceof Number number) {
                counts.put(id.longValue(), number.longValue());
            }
        }
        return counts;
    }

    /** 当前页漏洞对应的依赖（一次批量查，避免 N+1）。 */
    private Map<Long, Dependency> dependenciesById(List<Vulnerability> rows) {
        List<Long> ids = rows.stream()
                .map(Vulnerability::getDependencyId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Dependency> map = new HashMap<>(ids.size() * 2);
        if (ids.isEmpty()) {
            return map;
        }
        for (Dependency dep : dependencyMapper.selectBatchIds(ids)) {
            map.put(dep.getId(), dep);
        }
        return map;
    }

    /** 把 selectMaps 结果转成 键→数量。 */
    private Map<String, Long> toCountMap(List<Map<String, Object>> rows, String keyColumn, String countColumn) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> lower = lowerKeys(row);
            Object key = lower.get(keyColumn);
            Object count = lower.get(countColumn);
            if (key != null && count instanceof Number number) {
                map.put(String.valueOf(key), number.longValue());
            }
        }
        return map;
    }

    /** H2 返回的列名是大写，统一转小写再取值。 */
    private Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> lower = new HashMap<>(row.size() * 2);
        row.forEach((key, value) -> lower.put(key.toLowerCase(Locale.ROOT), value));
        return lower;
    }

    /** 补 0：保证前端拿到的分布有固定键。 */
    private Map<String, Long> fill(Map<String, Long> counts, String[] keys) {
        Map<String, Long> filled = new LinkedHashMap<>();
        for (String key : keys) {
            filled.put(key, counts.getOrDefault(key, 0L));
        }
        counts.forEach(filled::putIfAbsent);
        return filled;
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
