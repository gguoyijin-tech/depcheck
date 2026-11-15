package com.company.depcheck.engine.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 报告中的一个依赖。
 *
 * @author depcheck
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencyJson {

    /** 文件名；嵌套 jar 形如「外层.jar: 内层.jar」。 */
    private String fileName;

    /** 文件路径。 */
    private String filePath;

    /** 许可证（DC 给的是单数字符串，可能是名字、URL 或整段声明）。 */
    private String license;

    /** 依赖坐标（purl）。 */
    private List<PackageJson> packages;

    /** 该依赖上的漏洞。 */
    private List<VulnerabilityJson> vulnerabilities;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public List<PackageJson> getPackages() {
        return packages;
    }

    public void setPackages(List<PackageJson> packages) {
        this.packages = packages;
    }

    public List<VulnerabilityJson> getVulnerabilities() {
        return vulnerabilities;
    }

    public void setVulnerabilities(List<VulnerabilityJson> vulnerabilities) {
        this.vulnerabilities = vulnerabilities;
    }

    /**
     * 依赖坐标。
     *
     * @author depcheck
     * @since 1.0.0
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageJson {

        /** purl，如 pkg:maven/groupId:artifactId@version。 */
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
