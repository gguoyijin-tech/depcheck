package com.company.depcheck.model.vo;

import java.util.Map;

/**
 * 任务结果概览（结果页顶部卡片）。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class ScanSummaryVO {

    private Long taskId;
    private String taskNo;
    private String status;

    /** 依赖总数。 */
    private long dependencyCount;

    /** 漏洞总数。 */
    private long vulnerabilityCount;

    /** 按严重度分布：CRITICAL / HIGH / MEDIUM / LOW / UNKNOWN（无数据补 0）。 */
    private Map<String, Long> severityCounts;

    /** 按许可证风险分布：HIGH / MEDIUM / LOW / UNKNOWN（无数据补 0）。 */
    private Map<String, Long> licenseRiskCounts;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getDependencyCount() {
        return dependencyCount;
    }

    public void setDependencyCount(long dependencyCount) {
        this.dependencyCount = dependencyCount;
    }

    public long getVulnerabilityCount() {
        return vulnerabilityCount;
    }

    public void setVulnerabilityCount(long vulnerabilityCount) {
        this.vulnerabilityCount = vulnerabilityCount;
    }

    public Map<String, Long> getSeverityCounts() {
        return severityCounts;
    }

    public void setSeverityCounts(Map<String, Long> severityCounts) {
        this.severityCounts = severityCounts;
    }

    public Map<String, Long> getLicenseRiskCounts() {
        return licenseRiskCounts;
    }

    public void setLicenseRiskCounts(Map<String, Long> licenseRiskCounts) {
        this.licenseRiskCounts = licenseRiskCounts;
    }
}
