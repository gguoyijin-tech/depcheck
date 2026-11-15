package com.company.depcheck.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.Result;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.model.entity.Project;
import com.company.depcheck.model.vo.ProjectVO;
import com.company.depcheck.service.ProjectService;

/**
 * 项目查询与 ZIP 上传接口。
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    /** 会话标识请求头。 */
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    /** 默认会话标识（未传请求头时兜底）。 */
    public static final String DEFAULT_SESSION_ID = "default";

    private final ProjectService projectService;

    /**
     * 构造注入项目业务服务。
     *
     * @param projectService 项目业务服务
     */
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 查询当前会话下的项目列表（按创建时间倒序）。
     *
     * @param sessionId 会话标识
     * @return 项目视图列表
     */
    @GetMapping
    public Result<List<ProjectVO>> list(
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId) {
        List<ProjectVO> vos = projectService.listBySession(sessionId).stream().map(this::toVo).toList();
        return Result.ok(vos);
    }

    /**
     * 按 ID 查询当前会话下的项目详情。
     *
     * @param id        项目 ID
     * @param sessionId 会话标识
     * @return 项目视图
     */
    @GetMapping("/{id}")
    public Result<ProjectVO> detail(
            @PathVariable Long id,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId) {
        Project project = projectService.getById(id, sessionId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.ok(toVo(project));
    }

    /**
     * 上传项目 ZIP 包并解压入库。
     *
     * @param file      上传的 ZIP 文件
     * @param sessionId 会话标识
     * @return 新建的项目视图
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProjectVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HEADER_SESSION_ID, defaultValue = DEFAULT_SESSION_ID) String sessionId) {
        Project project = projectService.upload(file, sessionId);
        return Result.ok(toVo(project));
    }

    private ProjectVO toVo(Project project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setOriginalName(project.getOriginalName());
        vo.setSizeBytes(project.getSizeBytes());
        vo.setFileCount(project.getFileCount());
        vo.setCreatedAt(project.getCreatedAt());
        return vo;
    }
}
