package com.company.depcheck.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.depcheck.common.constant.TaskStage;
import com.company.depcheck.common.constant.TaskStatus;
import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.common.storage.StorageCleaner;
import com.company.depcheck.config.ScanProperties;
import com.company.depcheck.engine.ScanCanceledException;
import com.company.depcheck.engine.ScanEngine;
import com.company.depcheck.engine.ScanParameter;
import com.company.depcheck.engine.ScanResult;
import com.company.depcheck.engine.report.ReportJson;
import com.company.depcheck.engine.report.ReportParser;
import com.company.depcheck.mapper.ProjectMapper;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.Project;
import com.company.depcheck.model.entity.ScanTask;
import com.company.depcheck.service.ScanPersistService;

/**
 * 扫描任务执行器：在线程池里跑完"准备 → 扫描 → 解析 → 入库"，并把每一步写回任务表。
 *
 * <p>三条设计约束（见 04-5 / 04-7）：</p>
 * <ol>
 *   <li><b>单例 + 无状态</b>：任务 ID 只走方法参数，任何任务状态都不放成员变量，
 *       否则两个任务并行时会互相覆盖进度；</li>
 *   <li><b>无状态机跳变</b>：所有状态更新都带前置状态条件（{@code where status = ...}），
 *       取消、超时、成功三条路径只有一个能生效；</li>
 *   <li><b>取消/超时后不留脏数据</b>：结果丢弃前先删本次任务的报告目录。</li>
 * </ol>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class ScanTaskWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ScanTaskWorker.class);

    /** 开始执行时的进度（准备阶段）。 */
    private static final int PROGRESS_PREPARE = 5;

    /** 进入入库环节的进度（报告已生成，正在解析/写库）。 */
    private static final int PROGRESS_PERSIST = 95;

    /** error_msg 列上限 512，留出余量。 */
    private static final int MAX_ERROR_MSG_LEN = 500;

    /** 状态写入被忽略时的日志模板（任务已被取消或超时抢先改写）。 */
    private static final String SKIP_STATE_LOG = "任务已不在执行态，忽略状态写入 taskId={}";

    private final ScanTaskMapper scanTaskMapper;

    private final ProjectMapper projectMapper;

    private final ScanEngine scanEngine;

    private final ReportParser reportParser;

    private final ScanPersistService scanPersistService;

    private final ScanProperties scanProperties;

    private final StorageCleaner storageCleaner;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper     任务 Mapper
     * @param projectMapper      项目 Mapper
     * @param scanEngine         扫描引擎
     * @param reportParser       报告解析器
     * @param scanPersistService 入库服务
     * @param scanProperties     扫描配置（报告根目录）
     */
    public ScanTaskWorker(ScanTaskMapper scanTaskMapper, ProjectMapper projectMapper, ScanEngine scanEngine,
                          ReportParser reportParser, ScanPersistService scanPersistService,
                          ScanProperties scanProperties, StorageCleaner storageCleaner) {
        this.scanTaskMapper = scanTaskMapper;
        this.projectMapper = projectMapper;
        this.scanEngine = scanEngine;
        this.reportParser = reportParser;
        this.scanPersistService = scanPersistService;
        this.scanProperties = scanProperties;
        this.storageCleaner = storageCleaner;
    }

    /**
     * 执行一个任务（由线程池调用，必须吞掉所有异常）。
     *
     * @param taskId 任务 ID
     */
    public void runTask(Long taskId) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            LOG.warn("任务不存在，跳过 taskId={}", taskId);
            return;
        }
        // 排队期间被取消：不必扫描，直接收尾
        if (isCancelRequested(taskId)) {
            LOG.info("任务在开始前已被取消 taskId={}", taskId);
            markCanceled(taskId);
            return;
        }
        // QUEUED → RUNNING：条件更新，避免与取消/超时并发时踩掉对方的状态
        if (!markRunning(taskId)) {
            LOG.info("任务状态不是 QUEUED（可能已被取消），跳过执行 taskId={} status={}",
                    taskId, task.getStatus());
            return;
        }

        Path outDir = Path.of(scanProperties.getReportRoot(), task.getTaskNo());
        try {
            Project project = projectMapper.selectById(task.getProjectId());
            if (project == null) {
                markFailed(taskId, ResultCode.NOT_FOUND.getCode(), "项目不存在：" + task.getProjectId());
                return;
            }
            Path scanPath = Path.of(project.getStoragePath());
            if (!Files.exists(scanPath)) {
                markFailed(taskId, ResultCode.NOT_FOUND.getCode(), "项目文件已丢失：" + project.getId());
                return;
            }

            ScanParameter parameter = new ScanParameter();
            parameter.setProjectName(project.getName());
            parameter.setScanPath(scanPath);
            parameter.setOutputDir(outDir);
            parameter.setProgressListener((percent, stage) -> onProgress(taskId, percent, stage));

            ScanResult result = scanEngine.scan(parameter);
            LOG.info("引擎扫描结束 taskId={} deps={} engine耗时={}ms",
                    taskId, result.getDependencyCount(), result.getDurationMs());

            // 扫描结束 → 解析入库之间还有两步，先确认任务仍然有效（超时/取消可能刚刚发生）
            ScanTask current = scanTaskMapper.selectById(taskId);
            if (current == null || !TaskStatus.RUNNING.equals(current.getStatus())) {
                LOG.warn("任务状态已变为 {}，丢弃本次结果并清理报告目录 taskId={} outDir={}",
                        current == null ? "NULL" : current.getStatus(), taskId, outDir);
                storageCleaner.deleteReport(task.getTaskNo());
                return;
            }
            if (Integer.valueOf(1).equals(current.getCancelRequested())) {
                throw new ScanCanceledException();
            }

            updateProgress(taskId, PROGRESS_PERSIST, TaskStage.PERSIST);
            ReportJson report = reportParser.parse(result.getJsonReport().toFile());
            int depCount = scanPersistService.persist(taskId, report);
            LOG.info("任务完成 taskId={} deps={} 总耗时={}ms", taskId, depCount, result.getDurationMs());
        } catch (ScanCanceledException ex) {
            LOG.info("任务已取消，清理报告目录 taskId={} outDir={}", taskId, outDir);
            storageCleaner.deleteReport(task.getTaskNo());
            markCanceled(taskId);
        } catch (BusinessException ex) {
            LOG.error("任务失败 taskId={} code={} msg={}", taskId, ex.getCode(), ex.getMessage(), ex);
            markFailed(taskId, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            LOG.error("任务异常 taskId={}", taskId, ex);
            markFailed(taskId, ResultCode.SCAN_FAILED.getCode(), "扫描异常：" + ex.getMessage());
        }
    }

    /**
     * 进度回调：先看取消标记，再写库。
     *
     * <p>回调发生在引擎的两个阶段之间（20/40/80/90），在这里抛异常就能在
     * 进入下一个耗时阶段前退出——这就是"取消在阶段边界生效"的落点。</p>
     *
     * @param taskId  任务 ID
     * @param percent 进度
     * @param stage   阶段
     */
    private void onProgress(Long taskId, int percent, String stage) {
        if (isCancelRequested(taskId)) {
            throw new ScanCanceledException();
        }
        updateProgress(taskId, percent, stage);
    }

    /** QUEUED → RUNNING（带前置状态）。 */
    private boolean markRunning(Long taskId) {
        ScanTask update = new ScanTask();
        update.setStatus(TaskStatus.RUNNING);
        update.setProgress(PROGRESS_PREPARE);
        update.setStage(TaskStage.PREPARE);
        update.setStartedAt(LocalDateTime.now());
        return scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, taskId)
                .eq(ScanTask::getStatus, TaskStatus.QUEUED)) > 0;
    }

    /**
     * 标记失败（调度器拒绝任务时也会调用）。
     *
     * <p>只允许 QUEUED/RUNNING → FAILED；已被取消或已超时的任务不会被覆盖。</p>
     *
     * @param taskId  任务 ID
     * @param code    错误码
     * @param message 错误信息
     */
    public void markFailed(Long taskId, int code, String message) {
        LocalDateTime now = LocalDateTime.now();
        ScanTask old = scanTaskMapper.selectById(taskId);
        ScanTask update = new ScanTask();
        update.setStatus(TaskStatus.FAILED);
        update.setStage(TaskStage.FAILED);
        update.setErrorCode(code);
        update.setErrorMsg(truncate(message, MAX_ERROR_MSG_LEN));
        update.setFinishedAt(now);
        if (old != null && old.getStartedAt() != null) {
            update.setDurationMs(Duration.between(old.getStartedAt(), now).toMillis());
        }
        int rows = scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, taskId)
                .in(ScanTask::getStatus, TaskStatus.QUEUED, TaskStatus.RUNNING));
        if (rows == 0) {
            LOG.warn(SKIP_STATE_LOG, taskId);
        }
    }

    /** 标记取消（只允许 QUEUED/RUNNING → CANCELED）。 */
    private void markCanceled(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        ScanTask old = scanTaskMapper.selectById(taskId);
        ScanTask update = new ScanTask();
        update.setStatus(TaskStatus.CANCELED);
        update.setStage(TaskStage.CANCELED);
        update.setFinishedAt(now);
        if (old != null && old.getStartedAt() != null) {
            update.setDurationMs(Duration.between(old.getStartedAt(), now).toMillis());
        }
        int rows = scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, taskId)
                .in(ScanTask::getStatus, TaskStatus.QUEUED, TaskStatus.RUNNING));
        if (rows == 0) {
            // 排队阶段已被 ScanTaskService 直接置为 CANCELED，这里不用再写
            LOG.debug(SKIP_STATE_LOG, taskId);
        }
    }

    /** 只更新进度（任务已不在 RUNNING 时自动失效，不会把失败任务写回运行中）。 */
    private void updateProgress(Long taskId, int progress, String stage) {
        ScanTask update = new ScanTask();
        update.setProgress(progress);
        update.setStage(stage);
        int rows = scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                .eq(ScanTask::getId, taskId)
                .eq(ScanTask::getStatus, TaskStatus.RUNNING));
        if (rows == 0) {
            LOG.debug("任务已不在 RUNNING，忽略进度写入 taskId={} progress={}", taskId, progress);
        }
    }

    /** 读一次取消标记（取消是"标记 + 阶段检查"，不杀线程）。 */
    private boolean isCancelRequested(Long taskId) {
        ScanTask current = scanTaskMapper.selectById(taskId);
        return current != null && Integer.valueOf(1).equals(current.getCancelRequested());
    }

    /**
     * 截断超长字符串，防止 error_msg 列溢出。
     *
     * @param value 原值
     * @param max   上限
     * @return 截断后的值
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
