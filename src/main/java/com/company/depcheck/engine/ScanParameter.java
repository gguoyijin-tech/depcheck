package com.company.depcheck.engine;

import java.nio.file.Path;

/**
 * 一次扫描的入参。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class ScanParameter {

    /** 项目名，会写进报告的 projectInfo。 */
    private String projectName;

    /** 要扫描的目录或 jar 路径。 */
    private Path scanPath;

    /** 报告输出目录。 */
    private Path outputDir;
    /** 进度回调，可为 null（dev 调试时不传）。 */
    private ScanProgressListener progressListener;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Path getScanPath() {
        return scanPath;
    }

    public void setScanPath(Path scanPath) {
        this.scanPath = scanPath;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    public ScanProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(ScanProgressListener progressListener) {
        this.progressListener = progressListener;
    }

}
