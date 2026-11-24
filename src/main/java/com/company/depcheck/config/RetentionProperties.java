package com.company.depcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据保留策略，对应 application.yaml 的 depcheck.retention.*。
 *
 * <p>报告与解压目录都是"可再生"的中间产物：库里已有结论，删掉后重扫即可。
 * 所以按天清，而不是永久占盘；具体口径见 06-结项冲刺步骤 §3.3。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "depcheck.retention")
public class RetentionProperties {

    /** 失败任务的报告保留时长（分钟）：留现场排障，默认 24 小时。 */
    private int failedReportMinutes = 1440;

    /** 成功任务的报告保留天数：默认 7 天。 */
    private int successReportDays = 7;

    /**
     * 项目（含解压目录与任务历史）保留天数：默认 30 天。
     *
     * <p>大于 0 时，上传即写 {@code expire_at = now + projectDays}；
     * <b>小于等于 0 表示不自动清理项目</b>。历史遗留项目（{@code expire_at} 为 NULL）一律不清，
     * 避免误删历史扫描记录——需要时按手册里的 SQL 手动清理。</p>
     */
    private int projectDays = 30;

    public int getFailedReportMinutes() {
        return failedReportMinutes;
    }

    public void setFailedReportMinutes(int failedReportMinutes) {
        this.failedReportMinutes = failedReportMinutes;
    }

    public int getSuccessReportDays() {
        return successReportDays;
    }

    public void setSuccessReportDays(int successReportDays) {
        this.successReportDays = successReportDays;
    }

    public int getProjectDays() {
        return projectDays;
    }

    public void setProjectDays(int projectDays) {
        this.projectDays = projectDays;
    }
}
