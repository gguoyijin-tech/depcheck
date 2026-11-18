package com.company.depcheck.service;

import java.util.List;

import com.company.depcheck.model.vo.ScanTaskVO;

/**
 * 扫描任务业务接口。
 *
 * @author depcheck
 * @since 1.0.0
 */
public interface ScanTaskService {

    /**
     * 创建任务并提交线程池。
     *
     * @param projectId 项目 ID
     * @param sessionId 会话标识
     * @return 任务视图（status 通常为 QUEUED 或 RUNNING）
     */
    ScanTaskVO create(Long projectId, String sessionId);

    /**
     * 查询任务详情（校验会话归属）。
     *
     * @param id        任务 ID
     * @param sessionId 会话标识
     * @return 任务视图
     */
    ScanTaskVO getById(Long id, String sessionId);

    /**
     * 查询当前会话的任务列表。
     *
     * @param sessionId 会话标识
     * @param status    状态过滤，可为 null
     * @return 任务列表
     */
    List<ScanTaskVO> list(String sessionId, String status);

    /**
     * 请求取消任务（软取消：标记 + 阶段边界生效）。
     *
     * @param id        任务 ID
     * @param sessionId 会话标识
     */
    void cancel(Long id, String sessionId);
}
