package com.company.depcheck.service;

import com.company.depcheck.model.vo.DependencyVO;
import com.company.depcheck.model.vo.PageResult;
import com.company.depcheck.model.vo.ScanSummaryVO;
import com.company.depcheck.model.vo.VulnerabilityVO;

/**
 * 扫描结果查询：概览、依赖分页、漏洞分页。
 *
 * <p>数据来源是 D7 已入库的 {@code dependency} / {@code vulnerability}，
 * 本接口只读，不改任何状态。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
public interface ScanTaskQueryService {

    /**
     * 结果概览：依赖数、漏洞数、严重度与许可证分布。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话标识
     * @return 概览
     */
    ScanSummaryVO summary(Long taskId, String sessionId);

    /**
     * 依赖分页。
     *
     * @param taskId      任务 ID
     * @param sessionId   会话标识
     * @param page        页码，从 1 开始
     * @param size        每页条数
     * @param keyword     名称/purl 模糊搜索，可为 null
     * @param licenseRisk 许可证风险过滤（HIGH/MEDIUM/LOW/UNKNOWN），可为 null
     * @return 分页结果
     */
    PageResult<DependencyVO> listDependencies(Long taskId, String sessionId,
                                              int page, int size, String keyword, String licenseRisk);

    /**
     * 漏洞分页（按 CVSS 从高到低，空值排最后）。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话标识
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @param severity  严重度过滤（CRITICAL/HIGH/MEDIUM/LOW/UNKNOWN），可为 null
     * @return 分页结果
     */
    PageResult<VulnerabilityVO> listVulnerabilities(Long taskId, String sessionId,
                                                    int page, int size, String severity);

    /**
     * 取任务的 HTML 报告文件（用于下载/预览）。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话标识
     * @return 报告文件路径
     */
    java.nio.file.Path reportFile(Long taskId, String sessionId);
}
