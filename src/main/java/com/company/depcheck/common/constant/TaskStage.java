package com.company.depcheck.common.constant;

/**
 * 扫描阶段常量，对应 scan_task.stage。
 *
 * <p>DC 的 Engine 没有细粒度进度事件，只能按阶段上报：
 * PREPARE(5) → SCAN(20) → ANALYZE(40) → REPORT(80/90) → PERSIST(95) → DONE(100)。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
public final class TaskStage {

    /** 已入队，未开始。 */
    public static final String QUEUED = "QUEUED";

    /** 准备参数、检查工程目录。 */
    public static final String PREPARE = "PREPARE";

    /** 引擎采集依赖（engine.scan）。 */
    public static final String SCAN = "SCAN";

    /** 漏洞分析（engine.analyzeDependencies，最耗时）。 */
    public static final String ANALYZE = "ANALYZE";

    /** 生成报告（writeReports）。 */
    public static final String REPORT = "REPORT";

    /** 解析报告并批量入库。 */
    public static final String PERSIST = "PERSIST";

    /** 全部完成。 */
    public static final String DONE = "DONE";

    /** 失败。 */
    public static final String FAILED = "FAILED";

    /** 已取消。 */
    public static final String CANCELED = "CANCELED";

    /** 超时。 */
    public static final String TIMEOUT = "TIMEOUT";

    private TaskStage() {
    }
}
