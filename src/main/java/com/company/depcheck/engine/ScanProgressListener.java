package com.company.depcheck.engine;

/**
 * 扫描进度回调。引擎只上报阶段，不承诺百分比精确。
 *
 * @author depcheck
 * @since 1.0.0
 */
@FunctionalInterface
public interface ScanProgressListener {

    /**
     * 上报进度。
     *
     * @param percent 进度百分比 0-100
     * @param stage   阶段名
     */
    void onProgress(int percent, String stage);
}