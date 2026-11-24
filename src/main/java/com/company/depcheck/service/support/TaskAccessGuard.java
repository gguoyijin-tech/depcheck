package com.company.depcheck.service.support;

import org.springframework.stereotype.Component;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.common.util.SessionUtils;
import com.company.depcheck.mapper.ProjectMapper;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.Project;
import com.company.depcheck.model.entity.ScanTask;

/**
 * 任务归属校验：任务接口与结果接口共用的"这道门"。
 *
 * <p>不是自己的任务一律按"不存在"处理（返回 1003），不泄露别人有没有这个任务。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class TaskAccessGuard {

    private final ScanTaskMapper scanTaskMapper;

    private final ProjectMapper projectMapper;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper 任务 Mapper
     * @param projectMapper  项目 Mapper
     */
    public TaskAccessGuard(ScanTaskMapper scanTaskMapper, ProjectMapper projectMapper) {
        this.scanTaskMapper = scanTaskMapper;
        this.projectMapper = projectMapper;
    }

    /**
     * 查任务并校验会话归属。
     *
     * @param taskId    任务 ID
     * @param sessionId 会话标识
     * @return 任务实体
     * @throws BusinessException 不存在或不属于当前会话（1003）
     */
    public ScanTask loadOwned(Long taskId, String sessionId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null || !SessionUtils.normalize(sessionId).equals(project.getOwnerSession())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return task;
    }
}
