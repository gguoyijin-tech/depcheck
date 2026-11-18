package com.company.depcheck.task;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;

/**
 * 扫描任务调度器：把任务丢进线程池，队列满时按"系统繁忙"拒绝。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class ScanTaskDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ScanTaskDispatcher.class);

    private final ThreadPoolExecutor scanExecutor;

    private final ScanTaskWorker worker;

    /**
     * 构造注入线程池与 Worker。
     *
     * @param scanExecutor 扫描线程池（Bean 名 scanExecutor）
     * @param worker       任务执行器
     */
    public ScanTaskDispatcher(ThreadPoolExecutor scanExecutor, ScanTaskWorker worker) {
        this.scanExecutor = scanExecutor;
        this.worker = worker;
    }

    /**
     * 提交任务；线程池拒绝时把任务置为失败并抛"系统繁忙"。
     *
     * <p>traceId 是在 Web 线程的 MDC 里生成的，异步线程不会自动继承，
     * 这里手工透传，保证一次扫描的日志能用同一个 traceId 串起来。</p>
     *
     * @param taskId 任务 ID
     * @throws BusinessException 队列已满（code 1004）
     */
    public void dispatch(Long taskId) {
        Map<String, String> traceContext = MDC.getCopyOfContextMap();
        try {
            scanExecutor.execute(() -> {
                if (traceContext != null) {
                    MDC.setContextMap(traceContext);
                }
                try {
                    worker.runTask(taskId);
                } catch (Exception ex) {
                    LOG.error("扫描任务执行异常 taskId={}", taskId, ex);
                    worker.markFailed(taskId, ResultCode.SCAN_FAILED.getCode(), "扫描异常：" + ex.getMessage());
                } finally {
                    MDC.clear();
                }
            });
        } catch (RejectedExecutionException ex) {
            LOG.warn("扫描队列已满，拒绝任务 taskId={} poolSize={} queueSize={}",
                    taskId, scanExecutor.getPoolSize(), scanExecutor.getQueue().size());
            worker.markFailed(taskId, ResultCode.SERVER_BUSY.getCode(), ResultCode.SERVER_BUSY.getMessage());
            throw new BusinessException(ResultCode.SERVER_BUSY);
        }
    }
}
