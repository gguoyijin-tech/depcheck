package com.company.depcheck.config;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.depcheck.common.constant.TaskStage;
import com.company.depcheck.common.constant.TaskStatus;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.ScanTask;

/**
 * 超时看门狗：定期把"卡住的 RUNNING 任务"置为失败。
 *
 * <p>DC 的分析阶段（analyzeDependencies）不可中断，线程停不下来，
 * 所以这里只做<b>标记</b>：把任务置 FAILED + code 3002。任务线程跑完回到
 * {@code ScanTaskWorker} 后，会因为"状态不是 RUNNING"而丢弃结果并删掉报告目录
 * （见 04-5 §6、04-7 §4.4）。</p>
 *
 * <p>检查间隔 60 秒，可用 {@code depcheck.scan.watchdog-interval-ms} 覆盖。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class ScanTimeoutWatchdog {

    private static final Logger LOG = LoggerFactory.getLogger(ScanTimeoutWatchdog.class);

    /** 一次最多处理多少条，避免长事务/长列表。 */
    private static final int BATCH_LIMIT = 100;

    private final ScanTaskMapper scanTaskMapper;

    private final ScanProperties scanProperties;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper 任务 Mapper
     * @param scanProperties 扫描配置（超时阈值）
     */
    public ScanTimeoutWatchdog(ScanTaskMapper scanTaskMapper, ScanProperties scanProperties) {
        this.scanTaskMapper = scanTaskMapper;
        this.scanProperties = scanProperties;
    }

    /**
     * 扫描一次运行中的任务，超时的置为失败。
     */
    @Scheduled(fixedDelayString = "${depcheck.scan.watchdog-interval-ms:60000}",
            initialDelayString = "${depcheck.scan.watchdog-interval-ms:60000}")
    public void check() {
        int timeoutMinutes = scanProperties.getTimeoutMinutes();
        if (timeoutMinutes < 0) {
            LOG.debug("看门狗已关闭（timeout-minutes={}）", timeoutMinutes);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(timeoutMinutes);
        List<ScanTask> running = scanTaskMapper.selectList(new LambdaQueryWrapper<ScanTask>()
                .eq(ScanTask::getStatus, TaskStatus.RUNNING)
                .lt(ScanTask::getStartedAt, deadline)
                .last("LIMIT " + BATCH_LIMIT));
        if (running.isEmpty()) {
            return;
        }

        for (ScanTask task : running) {
            ScanTask update = new ScanTask();
            update.setStatus(TaskStatus.FAILED);
            update.setStage(TaskStage.TIMEOUT);
            update.setErrorCode(ResultCode.SCAN_TIMEOUT.getCode());
            update.setErrorMsg("扫描超时（超过 " + timeoutMinutes + " 分钟）");
            update.setFinishedAt(now);
            if (task.getStartedAt() != null) {
                update.setDurationMs(Duration.between(task.getStartedAt(), now).toMillis());
            }
            int rows = scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                    .eq(ScanTask::getId, task.getId())
                    .eq(ScanTask::getStatus, TaskStatus.RUNNING));
            if (rows > 0) {
                LOG.warn("任务超时已置为失败 taskId={} startedAt={} 阈值={}分钟",
                        task.getId(), task.getStartedAt(), timeoutMinutes);
            }
        }
    }
}
