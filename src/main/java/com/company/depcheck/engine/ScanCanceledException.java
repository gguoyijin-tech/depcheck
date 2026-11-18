package com.company.depcheck.engine;

/**
 * 取消信号：由进度回调在阶段边界抛出，用于中断一次正在执行的扫描。
 *
 * <p>DC 的 {@code Engine#scan}/{@code analyzeDependencies} 是阻塞调用、无法从外部打断，
 * 所以取消的生效点是"阶段边界"：引擎在 20/40/80/90 四个点回调进度，
 * 回调里发现取消标记就抛本异常，让 Worker 在进入下一个耗时阶段前退出。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
public class ScanCanceledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用固定提示构造。
     */
    public ScanCanceledException() {
        super("扫描已被取消");
    }
}
