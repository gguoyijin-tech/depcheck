package com.company.depcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 扫描相关配置，对应 application.yaml 的 depcheck.scan.*。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "depcheck.scan")
public class ScanProperties {

    /** NVD 漏洞库目录（必须是 01 手册建好的那套，不要指向业务库 ./data）。 */
    private String dataDir = "D:/dc/data";

    /** 报告输出根目录，下面按任务号分目录。 */
    private String reportRoot = "./data/reports";

    /** NVD API Key，可为空（库已用 datafeed 建好，空着不影响扫描）。 */
    private String nvdApiKey = "";

    /** 是否允许引擎自动更新漏洞库；内嵌模式必须 false，否则一次扫描可能等几小时。 */
    private boolean autoUpdate = false;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getReportRoot() {
        return reportRoot;
    }

    public void setReportRoot(String reportRoot) {
        this.reportRoot = reportRoot;
    }

    public String getNvdApiKey() {
        return nvdApiKey;
    }

    public void setNvdApiKey(String nvdApiKey) {
        this.nvdApiKey = nvdApiKey;
    }

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }
}
