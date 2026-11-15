package com.company.depcheck.engine;

/**
 * 依赖扫描引擎抽象。业务层只认这个接口，不感知具体实现。
 *
 * @author depcheck
 * @since 1.0.0
 */
public interface ScanEngine {

    /**
     * 执行一次完整扫描。
     *
     * @param parameter 扫描参数，不可为 null
     * @return 扫描结果（报告路径 + 依赖数 + 耗时）
     */
    ScanResult scan(ScanParameter parameter);
}
