package com.company.depcheck.model.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 创建扫描任务的入参。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class ScanTaskCreateRequest {

    /** 项目 ID。 */
    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}