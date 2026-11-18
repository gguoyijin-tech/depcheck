package com.company.depcheck.common.constant;

/**
 * 扫描任务状态常量，对应 scan_task.status。
 *
 * <p>状态机：QUEUED → RUNNING → SUCCESS；任何阶段可转 FAILED / CANCELED，
 * 超时由看门狗写 FAILED（阶段 TIMEOUT）。禁止在代码里写裸字符串，避免状态写错查不出来。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
public final class TaskStatus {

    /** 已受理，等线程池执行。 */
    public static final String QUEUED = "QUEUED";

    /** 正在扫描。 */
    public static final String RUNNING = "RUNNING";

    /** 成功，明细已入库。 */
    public static final String SUCCESS = "SUCCESS";

    /** 失败（含超时）。 */
    public static final String FAILED = "FAILED";

    /** 已取消。 */
    public static final String CANCELED = "CANCELED";

    private TaskStatus() {
    }
}
