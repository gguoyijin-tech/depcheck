package com.company.depcheck.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.data.nvdcve.DatabaseException;
import org.owasp.dependencycheck.exception.ExceptionCollection;
import org.owasp.dependencycheck.exception.ReportException;
import org.owasp.dependencycheck.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.company.depcheck.config.ScanProperties;

/**
 * 进程内调用 DC 的扫描引擎实现（默认）。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class InProcessDcEngine implements ScanEngine {

    private static final Logger LOG = LoggerFactory.getLogger(InProcessDcEngine.class);

    /** 报告格式常量。 */
    private static final String FORMAT_HTML = "HTML";
    private static final String FORMAT_JSON = "JSON";

    /** 默认产出文件名。 */
    private static final String HTML_REPORT_NAME = "dependency-check-report.html";
    private static final String JSON_REPORT_NAME = "dependency-check-report.json";

    private final ScanProperties scanProperties;

    /**
     * 构造注入扫描配置。
     *
     * @param scanProperties 扫描配置
     */
    public InProcessDcEngine(ScanProperties scanProperties) {
        this.scanProperties = scanProperties;
    }

    @Override
    public ScanResult scan(ScanParameter parameter) {
        if (parameter == null || parameter.getScanPath() == null || parameter.getOutputDir() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        long start = System.currentTimeMillis();
        Path outDir = parameter.getOutputDir();
        try {
            Files.createDirectories(outDir);
        } catch (IOException ex) {
            LOG.error("创建报告输出目录失败 outDir={}", outDir, ex);
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "创建报告目录失败", ex);
        }

        Settings settings = buildSettings();
        try (Engine engine = new Engine(settings)) {
            engine.scan(parameter.getScanPath().toFile());
            try {
                engine.analyzeDependencies();
            } catch (ExceptionCollection ex) {
                // 部分分析器失败不影响已收集的结果，记录完整异常后继续出报告
                LOG.warn("依赖分析部分失败，共 {} 个异常，继续生成报告", ex.getExceptions().size(), ex);
            }

            // 一次只能出一种格式，必须分开调（实测：传 "HTML,JSON" 会报找不到模板）
            engine.writeReports(parameter.getProjectName(), outDir.toFile(), FORMAT_HTML);
            engine.writeReports(parameter.getProjectName(), outDir.toFile(), FORMAT_JSON);

            ScanResult result = new ScanResult();
            result.setDependencyCount(engine.getDependencies().length);
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setHtmlReport(outDir.resolve(HTML_REPORT_NAME));
            result.setJsonReport(outDir.resolve(JSON_REPORT_NAME));
            LOG.info("扫描完成 project={} deps={} 耗时={}ms",
                    parameter.getProjectName(), result.getDependencyCount(), result.getDurationMs());
            return result;
        } catch (DatabaseException ex) {
            LOG.error("打开 NVD 漏洞库失败 dataDir={}", scanProperties.getDataDir(), ex);
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "漏洞库打开失败", ex);
        } catch (ReportException ex) {
            LOG.error("生成扫描报告失败 project={}", parameter.getProjectName(), ex);
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "报告生成失败", ex);
        }
    }

    /**
     * 组装 DC 配置：数据目录、关更新、关联网分析器。
     *
     * @return DC Settings
     */
    private Settings buildSettings() {
        Settings settings = new Settings();
        settings.setString(Settings.KEYS.DATA_DIRECTORY, scanProperties.getDataDir());
        settings.setBoolean(Settings.KEYS.AUTO_UPDATE, scanProperties.isAutoUpdate());

        // 这三个分析器都要联网，内网/离线环境必须关掉，否则每次扫描都会等超时
        settings.setBoolean(Settings.KEYS.ANALYZER_RETIREJS_ENABLED, false);
        settings.setBoolean(Settings.KEYS.ANALYZER_OSSINDEX_ENABLED, false);
        settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);

        if (StringUtils.isNotBlank(scanProperties.getNvdApiKey())) {
            settings.setString(Settings.KEYS.NVD_API_KEY, scanProperties.getNvdApiKey());
        }
        return settings;
    }
}
