package com.company.depcheck.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扫描任务线程池：手写 ThreadPoolExecutor（规范禁 Executors.newXxx），
 * 有界队列 + 命名线程 + 拒绝策略 + 优雅关闭。
 *
 * <p>并发度受 4 GB 内存约束：单任务峰值 0.4–0.6 GB（大工程 1.2–1.8 GB），
 * 因此默认"受理 8、并行 2"，参数从 depcheck.scan.pool.* 读取（见 04-7）。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /** 空闲线程保活时间（秒）。 */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    /** 线程名前缀。 */
    private static final String THREAD_NAME_PREFIX = "dc-scan-";

    /** 兜底核心线程数（= 实际并行度）。 */
    private static final int FALLBACK_CORE = 2;

    /** 兜底最大线程数。 */
    private static final int FALLBACK_MAX = 2;

    /** 兜底队列容量。 */
    private static final int FALLBACK_QUEUE = 8;

    private final ScanProperties scanProperties;

    /**
     * 构造注入扫描配置。
     *
     * @param scanProperties 扫描配置
     */
    public ThreadPoolConfig(ScanProperties scanProperties) {
        this.scanProperties = scanProperties;
    }

    /**
     * 扫描线程池。
     *
     * @return 线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor scanExecutor() {
        ScanProperties.Pool pool = scanProperties.getPool();
        int core = pool == null || pool.getCore() <= 0 ? FALLBACK_CORE : pool.getCore();
        int max = pool == null || pool.getMax() <= 0 ? FALLBACK_MAX : pool.getMax();
        int queue = pool == null || pool.getQueue() <= 0 ? FALLBACK_QUEUE : pool.getQueue();
        if (core > max) {
            LOG.warn("线程池配置非法 core={} > max={}，按 core=max 处理", core, max);
            core = max;
        }
        LOG.info("扫描线程池初始化 core={} max={} queue={}", core, max, queue);

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + seq.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };
        return new ThreadPoolExecutor(core, max, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
