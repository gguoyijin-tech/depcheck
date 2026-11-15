package com.company.depcheck.service;

import com.company.depcheck.engine.report.ReportJson;

/**
 * 扫描结果入库。
 *
 * @author depcheck
 * @since 1.0.0
 */
public interface ScanPersistService {

    /**
     * 保存一次扫描结果（任务 + 依赖 + 漏洞）。
     *
     * @param projectId  项目 ID
     * @param report     解析后的报告
     * @param durationMs 扫描耗时（毫秒）
     * @return 任务 ID
     */
    Long persist(Long projectId, ReportJson report, long durationMs);
}
