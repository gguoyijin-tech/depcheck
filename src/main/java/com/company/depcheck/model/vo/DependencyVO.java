package com.company.depcheck.model.vo;

/**
 * 依赖行视图（结果页依赖 Tab）。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class DependencyVO {

    private Long id;
    private String name;
    private String version;
    private String ecosystem;
    private String purl;
    private String filePath;
    private String licenseName;
    private String licenseRisk;
    /** 该依赖命中的漏洞数（聚合出来，省前端再查一次）。 */
    private Long vulnerabilityCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEcosystem() {
        return ecosystem;
    }

    public void setEcosystem(String ecosystem) {
        this.ecosystem = ecosystem;
    }

    public String getPurl() {
        return purl;
    }

    public void setPurl(String purl) {
        this.purl = purl;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getLicenseName() {
        return licenseName;
    }

    public void setLicenseName(String licenseName) {
        this.licenseName = licenseName;
    }

    public String getLicenseRisk() {
        return licenseRisk;
    }

    public void setLicenseRisk(String licenseRisk) {
        this.licenseRisk = licenseRisk;
    }

    public Long getVulnerabilityCount() {
        return vulnerabilityCount;
    }

    public void setVulnerabilityCount(Long vulnerabilityCount) {
        this.vulnerabilityCount = vulnerabilityCount;
    }
}
