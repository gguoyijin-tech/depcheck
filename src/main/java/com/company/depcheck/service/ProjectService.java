package com.company.depcheck.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.company.depcheck.model.entity.Project;

/**
 * 项目业务接口。
 *
 * @author depcheck
 * @since 1.0.0
 */
public interface ProjectService {

    /**
     * 查询全部项目（按创建时间倒序）。
     *
     * @return 项目列表
     */
    List<Project> listAll();

    /**
     * 查询指定会话下的项目（按创建时间倒序，防止水平越权）。
     *
     * @param sessionId 会话标识
     * @return 项目列表
     */
    List<Project> listBySession(String sessionId);

    /**
     * 按 ID 查询项目。
     *
     * @param id 项目 ID
     * @return 项目，不存在返回 null
     */
    Project getById(Long id);

    /**
     * 按 ID 查询指定会话下的项目（防止水平越权）。
     *
     * @param id        项目 ID
     * @param sessionId 会话标识
     * @return 项目，不存在或不属于当前会话时返回 null
     */
    Project getById(Long id, String sessionId);

    /**
     * 接收上传的 ZIP，校验、落盘、解压并入库。
     *
     * @param file      上传文件，不可为 null
     * @param sessionId 会话标识（D5 阶段先由请求头传入）
     * @return 项目记录
     */
    Project upload(MultipartFile file, String sessionId);
}
