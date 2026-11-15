package com.company.depcheck.engine.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DC JSON 报告的顶层结构（只声明用到的字段，其余忽略）。
 *
 * @author depcheck
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportJson {

    /** 引擎与库信息。 */
    private ScanInfo scanInfo;

    /** 依赖清单。 */
    private List<DependencyJson> dependencies;

    public ScanInfo getScanInfo() {
        return scanInfo;
    }

    public void setScanInfo(ScanInfo scanInfo) {
        this.scanInfo = scanInfo;
    }

    public List<DependencyJson> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyJson> dependencies) {
        this.dependencies = dependencies;
    }

    /**
     * 扫描信息。
     *
     * @author depcheck
     * @since 1.0.0
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScanInfo {

        /** 引擎版本。 */
        private String engineVersion;

        public String getEngineVersion() {
            return engineVersion;
        }

        public void setEngineVersion(String engineVersion) {
            this.engineVersion = engineVersion;
        }
    }
}
