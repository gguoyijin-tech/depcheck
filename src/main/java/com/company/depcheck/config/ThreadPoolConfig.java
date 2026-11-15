package com.company.depcheck.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扫描任务线程池：手写 ThreadPoolExecutor（规范禁 Executors.newXxx），
 * 有界队列 + 命名线程 + 拒绝策略 + 优雅关闭。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Configuration
public class ThreadPoolConfig {

    /** 核心线程数（DC 单任务内存开销较大，常态保持 2 个核心线程）。 */
    private static final int CORE_POOL_SIZE = 2;

    /** 最大线程数（满足 PRD 4.1.4 至少支持 5 个并发扫描任务）。 */
    private static final int MAX_POOL_SIZE = 5;

    /** 空闲线程保活时间（秒）。 */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    /** 队列容量：满了直接拒绝，返回"系统繁忙"。 */
    private static final int QUEUE_CAPACITY = 16;

    /** 线程名前缀。 */
    private static final String THREAD_NAME_PREFIX = "dc-scan-";

    /**
     * 扫描线程池。
     *
     * @return 线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor scanExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + seq.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
