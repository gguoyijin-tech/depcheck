package com.company.depcheck.engine;

import java.nio.file.Path;

/**
 * 一次扫描的结果。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class ScanResult {

    private int dependencyCount;
    private long durationMs;
    private Path htmlReport;
    private Path jsonReport;

    public int getDependencyCount() {
        return dependencyCount;
    }

    public void setDependencyCount(int dependencyCount) {
        this.dependencyCount = dependencyCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Path getHtmlReport() {
        return htmlReport;
    }

    public void setHtmlReport(Path htmlReport) {
        this.htmlReport = htmlReport;
    }

    public Path getJsonReport() {
        return jsonReport;
    }

    public void setJsonReport(Path jsonReport) {
        this.jsonReport = jsonReport;
    }
}
