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
     * 把扫描结果写入指定任务（更新任务状态 + 批量写依赖与漏洞）。
     *
     * <p>任务由 {@code ScanTaskService#create} 预先创建，这里只做"更新"，
     * 不再新建任务，避免一次扫描产生两条任务记录。</p>
     *
     * @param taskId 任务 ID
     * @param report 解析后的报告
     * @return 依赖条数
     * @throws com.company.depcheck.common.exception.BusinessException 任务不存在或状态已变更（已取消/超时）
     */
    int persist(Long taskId, ReportJson report);
}
