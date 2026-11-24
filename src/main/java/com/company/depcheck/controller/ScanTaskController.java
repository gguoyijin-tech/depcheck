package com.company.depcheck.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.company.depcheck.model.vo.DependencyVO;
import com.company.depcheck.model.vo.PageResult;
import com.company.depcheck.model.vo.ScanSummaryVO;
import com.company.depcheck.model.vo.ScanTaskVO;
import com.company.depcheck.model.vo.VulnerabilityVO;
import com.company.depcheck.service.ScanTaskQueryService;
import com.company.depcheck.service.ScanTaskService;

/**
 * 扫描任务接口：创建 / 详情 / 列表 / 取消 / 结果查询。
 *
 * <p>会话隔离沿用项目的 {@code X-Session-Id} 请求头，所有接口都校验归属。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scan-tasks")
public class ScanTaskController {

    private final ScanTaskService scanTaskService;

    private final ScanTaskQueryService scanTaskQueryService;

    /**
     * 构造注入。
     *
     * @param scanTaskService      任务业务服务
     * @param scanTaskQueryService 结果查询服务
     */
    public ScanTaskController(ScanTaskService scanTaskService, ScanTaskQueryService scanTaskQueryService) {
        this.scanTaskService = scanTaskService;
        this.scanTaskQueryService = scanTaskQueryService;
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

    /**
     * 结果概览：依赖数、漏洞数、严重度与许可证分布。
     *
     * @param id        任务 ID
     * @param sessionId 会话标识
     * @return 概览
     */
    @GetMapping("/{id}/summary")
    public Result<ScanSummaryVO> summary(
            @PathVariable Long id,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        return Result.ok(scanTaskQueryService.summary(id, sessionId));
    }

    /**
     * 依赖分页（结果页依赖 Tab）。
     *
     * @param id          任务 ID
     * @param page        页码，从 1 开始
     * @param size        每页条数（上限 200）
     * @param keyword     名称/purl 模糊搜索
     * @param licenseRisk 许可证风险过滤
     * @param sessionId   会话标识
     * @return 分页结果
     */
    @GetMapping("/{id}/dependencies")
    public Result<PageResult<DependencyVO>> dependencies(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String licenseRisk,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        return Result.ok(scanTaskQueryService.listDependencies(id, sessionId, page, size, keyword, licenseRisk));
    }

    /**
     * 漏洞分页（结果页漏洞 Tab，按 CVSS 从高到低）。
     *
     * @param id        任务 ID
     * @param page      页码，从 1 开始
     * @param size      每页条数（上限 200）
     * @param severity  严重度过滤
     * @param sessionId 会话标识
     * @return 分页结果
     */
    @GetMapping("/{id}/vulnerabilities")
    public Result<PageResult<VulnerabilityVO>> vulnerabilities(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        return Result.ok(scanTaskQueryService.listVulnerabilities(id, sessionId, page, size, severity));
    }

    /**
     * 下载 HTML 报告（PRD 3.2.3 报告下载）。
     *
     * @param id        任务 ID
     * @param sessionId 会话标识
     * @return HTML 文件流
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<Resource> report(
            @PathVariable Long id,
            @RequestHeader(value = ProjectController.HEADER_SESSION_ID,
                    defaultValue = ProjectController.DEFAULT_SESSION_ID) String sessionId) {
        Path file = scanTaskQueryService.reportFile(id, sessionId);
        String fileName = "dependency-check-" + id + ".html";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.TEXT_HTML)
                .body(new FileSystemResource(file));
    }
}
