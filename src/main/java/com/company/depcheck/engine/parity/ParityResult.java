package com.company.depcheck.engine.parity;

import java.util.ArrayList;
import java.util.List;

/**
 * 一致性自检结果：同一工程分别用官方 CLI 与进程内引擎扫描后的差异。
 *
 * @author depcheck
 * @since 1.0.0
 */
public class ParityResult {

    /** CLI 依赖数。 */
    private int cliDependencies;

    /** 进程内依赖数。 */
    private int procDependencies;

    /** CLI 漏洞数。 */
    private int cliVulnerabilities;

    /** 进程内漏洞数。 */
    private int procVulnerabilities;

    /** 仅 CLI 有的依赖（fileName）。 */
    private List<String> depsOnlyInCli = new ArrayList<>();

    /** 仅进程内有的依赖。 */
    private List<String> depsOnlyInProc = new ArrayList<>();

    /** 仅 CLI 有的漏洞（fileName | CVE）。 */
    private List<String> vulnsOnlyInCli = new ArrayList<>();

    /** 仅进程内有的漏洞。 */
    private List<String> vulnsOnlyInProc = new ArrayList<>();

    /** 是否完全一致。 */
    private boolean equal;

    public int getCliDependencies() {
        return cliDependencies;
    }

    public void setCliDependencies(int cliDependencies) {
        this.cliDependencies = cliDependencies;
    }

    public int getProcDependencies() {
        return procDependencies;
    }

    public void setProcDependencies(int procDependencies) {
        this.procDependencies = procDependencies;
    }

    public int getCliVulnerabilities() {
        return cliVulnerabilities;
    }

    public void setCliVulnerabilities(int cliVulnerabilities) {
        this.cliVulnerabilities = cliVulnerabilities;
    }

    public int getProcVulnerabilities() {
        return procVulnerabilities;
    }

    public void setProcVulnerabilities(int procVulnerabilities) {
        this.procVulnerabilities = procVulnerabilities;
    }

    public List<String> getDepsOnlyInCli() {
        return depsOnlyInCli;
    }

    public void setDepsOnlyInCli(List<String> depsOnlyInCli) {
        this.depsOnlyInCli = depsOnlyInCli;
    }

    public List<String> getDepsOnlyInProc() {
        return depsOnlyInProc;
    }

    public void setDepsOnlyInProc(List<String> depsOnlyInProc) {
        this.depsOnlyInProc = depsOnlyInProc;
    }

    public List<String> getVulnsOnlyInCli() {
        return vulnsOnlyInCli;
    }

    public void setVulnsOnlyInCli(List<String> vulnsOnlyInCli) {
        this.vulnsOnlyInCli = vulnsOnlyInCli;
    }

    public List<String> getVulnsOnlyInProc() {
        return vulnsOnlyInProc;
    }

    public void setVulnsOnlyInProc(List<String> vulnsOnlyInProc) {
        this.vulnsOnlyInProc = vulnsOnlyInProc;
    }

    public boolean isEqual() {
        return equal;
    }

    public void setEqual(boolean equal) {
        this.equal = equal;
    }
}
