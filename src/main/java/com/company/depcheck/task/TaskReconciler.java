package com.company.depcheck.task;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.depcheck.common.constant.TaskStage;
import com.company.depcheck.common.constant.TaskStatus;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.common.storage.StorageCleaner;
import com.company.depcheck.mapper.ScanTaskMapper;
import com.company.depcheck.model.entity.ScanTask;

/**
 * 启动对账：把上一个进程留下的"半死"任务收尾。
 *
 * <p>单实例部署下，应用启动的那一刻不可能还有任务在跑——线程池与队列都在内存里，
 * 随进程一起消失。所以启动时把 {@code QUEUED} / {@code RUNNING} 一律置为
 * {@code FAILED}(3003)，并清掉它们的报告目录。</p>
 *
 * <p>不做这件事的后果（实测）：进程被 kill 后任务永远停在 RUNNING，
 * 页面一直转圈，要等看门狗 30 分钟才收；QUEUED 任务则永远不执行也不失败。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class TaskReconciler implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TaskReconciler.class);

    private final ScanTaskMapper scanTaskMapper;

    private final StorageCleaner storageCleaner;

    /**
     * 构造注入。
     *
     * @param scanTaskMapper 任务 Mapper
     * @param storageCleaner 磁盘清理
     */
    public TaskReconciler(ScanTaskMapper scanTaskMapper, StorageCleaner storageCleaner) {
        this.scanTaskMapper = scanTaskMapper;
        this.storageCleaner = storageCleaner;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ScanTask> stale = scanTaskMapper.selectList(new LambdaQueryWrapper<ScanTask>()
                .in(ScanTask::getStatus, TaskStatus.QUEUED, TaskStatus.RUNNING));
        if (stale.isEmpty()) {
            LOG.info("启动对账：没有未完成任务");
            return;
        }
        for (ScanTask task : stale) {
            ScanTask update = new ScanTask();
            update.setStatus(TaskStatus.FAILED);
            update.setStage(TaskStage.FAILED);
            update.setErrorCode(ResultCode.SCAN_INTERRUPTED.getCode());
            update.setErrorMsg("服务重启，任务中断（启动对账）");
            update.setFinishedAt(LocalDateTime.now());
            int rows = scanTaskMapper.update(update, new LambdaUpdateWrapper<ScanTask>()
                    .eq(ScanTask::getId, task.getId())
                    .in(ScanTask::getStatus, TaskStatus.QUEUED, TaskStatus.RUNNING));
            if (rows > 0) {
                storageCleaner.deleteReport(task.getTaskNo());
                LOG.warn("启动对账：任务 {}（原状态 {}）置为 FAILED/3003", task.getTaskNo(), task.getStatus());
            }
        }
        LOG.warn("启动对账完成，共收尾 {} 个中断任务", stale.size());
    }
}
