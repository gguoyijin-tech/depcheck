package com.company.depcheck.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 扫描任务实体，对应表 scan_task。
 *
 * @author depcheck
 * @since 1.0.0
 */
@TableName("scan_task")
public class ScanTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long projectId;
    private String status;
    private Integer progress;
    private String scanMode;
    private String engineVersion;
    private String nvdVersion;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Integer errorCode;
    private String errorMsg;
    private LocalDateTime createdAt;
    /** 是否请求取消：0 否，1 是。 */
    private Integer cancelRequested;

    /** 当前阶段名，如 PREPARE / SCAN / ANALYZE / REPORT / PERSIST。 */
    private String stage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getScanMode() {
        return scanMode;
    }

    public void setScanMode(String scanMode) {
        this.scanMode = scanMode;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getNvdVersion() {
        return nvdVersion;
    }

    public void setNvdVersion(String nvdVersion) {
        this.nvdVersion = nvdVersion;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(Integer cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    public String getStage() {
        return stage;
    }
    public void setStage(String stage) {
        this.stage = stage;
    }
}