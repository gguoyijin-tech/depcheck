package com.company.depcheck.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.depcheck.common.constant.TaskStage;
import com.company.depcheck.common.constant.TaskStatus;
import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.common.util.SessionUtils;
import com.company.depcheck.config.ScanProperties;
import com.company.depcheck.mapper.ProjectMapper;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.Project;
import com.company.depcheck.model.entity.ScanTask;
import com.company.depcheck.model.vo.ScanTaskVO;
import com.company.depcheck.service.ScanTaskService;
import com.company.depcheck.service.support.TaskAccessGuard;
import com.company.depcheck.task.ScanTaskDispatcher;

/**
 * 扫描任务业务实现：建任务 → 丢线程池 → 状态/进度可查 → 可取消。
 *
 * <p>注意 {@link #create} <b>故意不加事务</b>：任务行必须先提交，
 * 否则异步线程（另一个连接）读不到这条记录，会直接按"任务不存在"退出。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Service
public class ScanTaskServiceImpl implements ScanTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(ScanTaskServiceImpl.class);

    /** 引擎产出的 HTML 报告文件名。 */
    private static final String REPORT_FILE_NAME = "dependency-check-report.html";

    /** 任务号长度（够用且好复制）。 */
    private static final int TASK_NO_LENGTH = 16;

    /** 列表返回上限（项目还没引入分页插件）。 */
    private static final String LIMIT_SQL = "LIMIT 200";

    /** 扫描模式：只做构建级扫描。 */
    private static final String SCAN_MODE_BUILD = "BUILD";

    /** 已结束状态：不可再取消。 */
    private static final Set<String> FINAL_STATUS =
            Set.of(TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELED);

    /** 全部合法状态（用于过滤参数校验）。 */
    private static final Set<String> ALL_STATUS = Set.of(TaskStatus.QUEUED, TaskStatus.RUNNING,
            TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELED);

    /** 取消标记取值。 */
    private static final int CANCEL_REQUESTED = 1;

    private final ScanTaskMapper scanTaskMapper;

    private final ProjectMapper projectMapper;

    private final ScanTaskDispatcher dispatcher;

    private final TaskAccessGuard taskAccessGuard;

    private final ScanProperties scanProperties;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper  任务 Mapper
     * @param projectMapper   项目 Mapper
     * @param dispatcher      任务调度器
     * @param taskAccessGuard 任务归属校验
     */
    public ScanTaskServiceImpl(ScanTaskMapper scanTaskMapper, ProjectMapper projectMapper,
                               ScanTaskDispatcher dispatcher, TaskAccessGuard taskAccessGuard,
                               ScanProperties scanProperties) {
        this.scanTaskMapper = scanTaskMapper;
        this.projectMapper = projectMapper;
        this.dispatcher = dispatcher;
        this.taskAccessGuard = taskAccessGuard;
        this.scanProperties = scanProperties;
    }

    @Override
    public ScanTaskVO create(Long projectId, String sessionId) {
        String normalized = SessionUtils.normalize(sessionId);
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null || !normalized.equals(project.getOwnerSession())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        ScanTask task = new ScanTask();
        task.setTaskNo(UUID.randomUUID().toString().replace("-", "").substring(0, TASK_NO_LENGTH));
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.QUEUED);
        task.setProgress(0);
        task.setStage(TaskStage.QUEUED);
        task.setScanMode(SCAN_MODE_BUILD);
        task.setCancelRequested(0);
        task.setCreatedAt(LocalDateTime.now());
        scanTaskMapper.insert(task);
        LOG.info("任务已创建 taskId={} taskNo={} projectId={}", task.getId(), task.getTaskNo(), projectId);

        dispatcher.dispatch(task.getId());
        return toVo(scanTaskMapper.selectById(task.getId()));
    }

    @Override
    public ScanTaskVO getById(Long id, String sessionId) {
        return toVo(taskAccessGuard.loadOwned(id, sessionId));
    }

    @Override
    public List<ScanTaskVO> list(String sessionId, String status) {
        String normalized = SessionUtils.normalize(sessionId);
        String statusFilter = normalizeStatus(status);

        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .select(Project::getId)
                .eq(Project::getOwnerSession, normalized));
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();

        LambdaQueryWrapper<ScanTask> wrapper = new LambdaQueryWrapper<ScanTask>()
                .in(ScanTask::getProjectId, projectIds)
                .orderByDesc(ScanTask::getId)
                .last(LIMIT_SQL);
        if (statusFilter != null) {
            wrapper.eq(ScanTask::getStatus, statusFilter);
        }
        return scanTaskMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    public void cancel(Long id, String sessionId) {
        ScanTask task = taskAccessGuard.loadOwned(id, sessionId);
        if (FINAL_STATUS.contains(task.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "任务已结束，无法取消");
        }

        // ① 还在排队：没有被任何线程持有，可以直接收尾，用户立刻看到 CANCELED。
        //    Worker 稍后拿到这个任务时会发现状态不是 QUEUED，直接跳过。
        ScanTask queuedUpdate = new ScanTask();
        queuedUpdate.setStatus(TaskStatus.CANCELED);
        queuedUpdate.setStage(TaskStage.CANCELED);
        queuedUpdate.setCancelRequested(CANCEL_REQUESTED);
        queuedUpdate.setFinishedAt(LocalDateTime.now());
        int canceledRows = scanTaskMapper.update(queuedUpdate, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, id)
                .eq(ScanTask::getStatus, TaskStatus.QUEUED));
        if (canceledRows > 0) {
            LOG.info("任务在排队阶段被取消 taskId={}", id);
            return;
        }

        // ② 已经在跑：DC 的分析阶段不可中断，只能打标记，等下一个阶段边界生效
        ScanTask flagUpdate = new ScanTask();
        flagUpdate.setCancelRequested(CANCEL_REQUESTED);
        int rows = scanTaskMapper.update(flagUpdate, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, id)
                .eq(ScanTask::getStatus, TaskStatus.RUNNING));
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "任务已结束，无法取消");
        }
        LOG.info("已请求取消（阶段边界生效）taskId={} stage={} progress={}%",
                id, task.getStage(), task.getProgress());
    }

    /** 校验状态过滤值，非法值直接 1001，避免静默返回空列表。 */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!ALL_STATUS.contains(upper)) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "未知的任务状态：" + status);
        }
        return upper;
    }

    /** 实体转视图。 */
    private ScanTaskVO toVo(ScanTask task) {
        ScanTaskVO vo = new ScanTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setProjectId(task.getProjectId());
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setStage(task.getStage());
        vo.setDurationMs(task.getDurationMs());
        vo.setErrorCode(task.getErrorCode());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setHasReport(Files.isRegularFile(Path.of(scanProperties.getReportRoot(),
                task.getTaskNo(), REPORT_FILE_NAME)));
        return vo;
    }
}
