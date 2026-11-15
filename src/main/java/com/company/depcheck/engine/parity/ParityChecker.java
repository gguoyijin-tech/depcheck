package com.company.depcheck.engine.parity;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.config.ScanProperties;
import com.company.depcheck.engine.ScanEngine;
import com.company.depcheck.engine.ScanParameter;
import com.company.depcheck.engine.ScanResult;
import com.company.depcheck.engine.report.DependencyJson;
import com.company.depcheck.engine.report.ReportJson;
import com.company.depcheck.engine.report.ReportParser;
import com.company.depcheck.engine.report.VulnerabilityJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 一致性自检：同一个工程分别用官方 CLI 与进程内引擎扫描，比对依赖与漏洞明细。
 *
 * <p>用途：防止"依赖冲突"或"分析器开关遗漏"导致扫描结果失真。两侧必须使用同一组分析器开关，
 * 任何一侧偏离基线（petclinic：85 依赖 / 53 漏洞）都说明配置漂移。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class ParityChecker {

    private static final Logger LOG = LoggerFactory.getLogger(ParityChecker.class);

    /** CLI 执行超时（分钟）。 */
    private static final long CLI_TIMEOUT_MINUTES = 10L;

    /** 报告文件名。 */
    private static final String JSON_REPORT = "dependency-check-report.json";

    private final ScanEngine scanEngine;

    private final ReportParser reportParser;

    private final ScanProperties scanProperties;

    /**
     * 构造注入。
     *
     * @param scanEngine     进程内引擎
     * @param reportParser   报告解析器
     * @param scanProperties 扫描配置（含 CLI 路径与数据目录）
     */
    public ParityChecker(ScanEngine scanEngine, ReportParser reportParser, ScanProperties scanProperties) {
        this.scanEngine = scanEngine;
        this.reportParser = reportParser;
        this.scanProperties = scanProperties;
    }

    /**
     * 执行一次一致性比对。
     *
     * @param projectPath 待扫描的工程目录或 jar
     * @return 比对结果
     */
    public ParityResult check(Path projectPath) {
        if (projectPath == null || !Files.exists(projectPath)) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "扫描路径不存在");
        }
        String stamp = String.valueOf(System.currentTimeMillis());
        Path cliDir = Path.of(scanProperties.getReportRoot(), "parity-cli-" + stamp);
        Path procDir = Path.of(scanProperties.getReportRoot(), "parity-proc-" + stamp);

        ReportJson cliReport = runCli(projectPath, cliDir);
        ReportJson procReport = runInProcess(projectPath, procDir);
        return compare(cliReport, procReport);
    }

    /** 跑官方 CLI（与进程内使用同一组分析器开关）。 */
    private ReportJson runCli(Path projectPath, Path outDir) {
        try {
            Files.createDirectories(outDir);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "创建 CLI 报告目录失败", ex);
        }
        String command = String.join(" ",
                quote(scanProperties.getCliPath()),
                "-s", quote(projectPath.toAbsolutePath().toString()),
                "-o", quote(outDir.toAbsolutePath().toString()),
                "-f", "JSON",
                "--project", "parity",
                "--noupdate",
                "--data", quote(scanProperties.getDataDir()),
                "--disableRetireJS", "--disableOssIndex", "--disableCentral");

        ProcessBuilder builder = new ProcessBuilder("cmd", "/c", command);
        builder.redirectErrorStream(true);
        builder.environment().putIfAbsent("JAVA_HOME", System.getProperty("java.home"));
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(CLI_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "CLI 执行超时");
            }
            if (process.exitValue() != 0 && !Files.exists(outDir.resolve(JSON_REPORT))) {
                LOG.error("CLI 执行失败 exit={} output={}", process.exitValue(), tail(output));
                throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "CLI 执行失败：" + tail(output));
            }
            return reportParser.parse(outDir.resolve(JSON_REPORT).toFile());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("调用 CLI 失败", ex);
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "调用 CLI 失败", ex);
        }
    }

    /** 跑进程内引擎。 */
    private ReportJson runInProcess(Path projectPath, Path outDir) {
        ScanParameter parameter = new ScanParameter();
        parameter.setProjectName("parity");
        parameter.setScanPath(projectPath);
        parameter.setOutputDir(outDir);
        ScanResult result = scanEngine.scan(parameter);
        return reportParser.parse(result.getJsonReport().toFile());
    }

    /** 比对两份报告：依赖按 fileName，漏洞按 (fileName, CVE)。 */
    private ParityResult compare(ReportJson cli, ReportJson proc) {
        List<DependencyJson> cliDeps = cli.getDependencies() == null ? List.of() : cli.getDependencies();
        List<DependencyJson> procDeps = proc.getDependencies() == null ? List.of() : proc.getDependencies();

        Set<String> cliDepSet = new HashSet<>();
        for (DependencyJson d : cliDeps) {
            cliDepSet.add(String.valueOf(d.getFileName()));
        }
        Set<String> procDepSet = new HashSet<>();
        for (DependencyJson d : procDeps) {
            procDepSet.add(String.valueOf(d.getFileName()));
        }

        Set<String> cliVulnSet = vulnKeys(cliDeps);
        Set<String> procVulnSet = vulnKeys(procDeps);

        ParityResult result = new ParityResult();
        result.setCliDependencies(cliDepSet.size());
        result.setProcDependencies(procDepSet.size());
        result.setCliVulnerabilities(cliVulnSet.size());
        result.setProcVulnerabilities(procVulnSet.size());
        result.setDepsOnlyInCli(onlyIn(cliDepSet, procDepSet));
        result.setDepsOnlyInProc(onlyIn(procDepSet, cliDepSet));
        result.setVulnsOnlyInCli(onlyIn(cliVulnSet, procVulnSet));
        result.setVulnsOnlyInProc(onlyIn(procVulnSet, cliVulnSet));
        boolean equal = result.getDepsOnlyInCli().isEmpty() && result.getDepsOnlyInProc().isEmpty()
                && result.getVulnsOnlyInCli().isEmpty() && result.getVulnsOnlyInProc().isEmpty()
                && cliDepSet.size() == procDepSet.size() && cliVulnSet.size() == procVulnSet.size();
        result.setEqual(equal);

        LOG.info("一致性自检完成 equal={} cliDeps={} procDeps={} cliVulns={} procVulns={}",
                equal, cliDepSet.size(), procDepSet.size(), cliVulnSet.size(), procVulnSet.size());
        return result;
    }

    private Set<String> vulnKeys(List<DependencyJson> deps) {
        Set<String> keys = new HashSet<>();
        for (DependencyJson d : deps) {
            if (d.getVulnerabilities() == null) {
                continue;
            }
            for (VulnerabilityJson v : d.getVulnerabilities()) {
                keys.add(d.getFileName() + "|" + v.getName());
            }
        }
        return keys;
    }

    private List<String> onlyIn(Set<String> left, Set<String> right) {
        List<String> diff = new ArrayList<>();
        for (String item : left) {
            if (!right.contains(item)) {
                diff.add(item);
            }
        }
        diff.sort(String::compareTo);
        return diff;
    }

    private String quote(String value) {
        return "\"" + value + "\"";
    }

    private String tail(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(trimmed.length() - 300);
    }

    /**
     * 清理本次比对产生的临时报告目录。
     *
     * @param reportRoot 报告根目录
     */
    public void cleanup(Path reportRoot) {
        // 保留现场，方便人工核对；如需清理可在此实现
        LOG.debug("一致性报告保留在 {}", reportRoot);
    }

    /**
     * 判断文件是否存在（供控制器校验）。
     *
     * @param path 路径
     * @return 是否存在
     */
    public boolean exists(Path path) {
        return path != null && new File(path.toString()).exists();
    }
}
