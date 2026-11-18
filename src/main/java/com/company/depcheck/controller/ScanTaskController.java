package com.company.depcheck.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.depcheck.common.result.Result;
import com.company.depcheck.model.dto.ScanTaskCreateRequest;
import com.company.depcheck.model.vo.ScanTaskVO;
import com.company.depcheck.service.ScanTaskService;

/**
 * 扫描任务接口：创建 / 详情 / 列表 / 取消。
 *
 * <p>会话隔离沿用项目的 {@code X-Session-Id} 请求头，四组接口都校验归属。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scan-tasks")
public class ScanTaskController {

    private final ScanTaskService scanTaskService;

    /**
     * 构造注入任务业务服务。
     *
     * @param scanTaskService 任务业务服务
     */
    public ScanTaskController(ScanTaskService scanTaskService) {
        this.scanTaskService = scanTaskService;
    }

    /**
     * 提交扫描任务（异步执行，立即返回 taskId）。
     *
     * @param request   入参（projectId）
     * @param sessionId 会话标识
     * @return 任务视图
     */
    @PostMapping
    public Result<ScanTaskVO> create(
            @Valid @RequestBody ScanTaskCreateRequest request,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        return Result.ok(scanTaskService.create(request.getProjectId(), sessionId));
    }

    /**
     * 查询任务详情（前端 1–3 秒轮询这个接口看进度）。
     *
     * @param id        任务 ID
     * @param sessionId 会话标识
     * @return 任务视图
     */
    @GetMapping("/{id}")
    public Result<ScanTaskVO> detail(
            @PathVariable Long id,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        return Result.ok(scanTaskService.getById(id, sessionId));
    }

    /**
     * 查询当前会话的任务列表。
     *
     * @param status    状态过滤（QUEUED/RUNNING/SUCCESS/FAILED/CANCELED），可为空
     * @param sessionId 会话标识
     * @return 任务列表
     */
    @GetMapping
    public Result<List<ScanTaskVO>> list(
            @RequestParam(required = false) String status,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        return Result.ok(scanTaskService.list(sessionId, status));
    }

    /**
     * 请求取消任务（软取消，下一个阶段边界生效）。
     *
     * @param id        任务 ID
     * @param sessionId 会话标识
     * @return 空成功响应
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(
            @PathVariable Long id,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        scanTaskService.cancel(id, sessionId);
        return Result.ok();
    }
}
