package com.company.depcheck.dev;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.company.depcheck.engine.ScanEngine;
import com.company.depcheck.engine.ScanParameter;
import com.company.depcheck.engine.ScanResult;
import com.company.depcheck.engine.report.ReportJson;
import com.company.depcheck.engine.report.ReportParser;
import com.company.depcheck.service.ScanPersistService;

/**
 * D6/D7 临时验证入口：启动时扫一次指定目录，解析报告并入库。
 * TODO(depcheck, 2026-11-18): D8 接入异步任务表后移除本类。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "dev.scan-on-startup", havingValue = "true")
public class DevScanRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevScanRunner.class);

    private static final String DEFAULT_SCAN_PATH = "D:\\dc\\samples\\spring-petclinic";
    private static final String DEFAULT_OUT_DIR = "./data/reports/dev";
    private static final String DEV_PROJECT_NAME = "dev-scan";

    /** 临时借用的项目 ID（D8 换成真实 projectId）。 */
    private static final Long DEV_PROJECT_ID = 1L;

    private final ScanEngine scanEngine;

    private final ReportParser reportParser;

    private final ScanPersistService scanPersistService;

    /**
     * 构造注入。
     *
     * @param scanEngine         扫描引擎
     * @param reportParser       报告解析器
     * @param scanPersistService 入库服务
     */
    public DevScanRunner(ScanEngine scanEngine, ReportParser reportParser, ScanPersistService scanPersistService) {
        this.scanEngine = scanEngine;
        this.reportParser = reportParser;
        this.scanPersistService = scanPersistService;
    }

    /**
     * 启动后执行一次「扫描 → 解析 → 入库」验证。
     *
     * @param args 启动参数
     */
    @Override
    public void run(String... args) {
        String scanPath = System.getProperty("dev.scan-path", DEFAULT_SCAN_PATH);
        String outDir = System.getProperty("dev.out-dir", DEFAULT_OUT_DIR);

        ScanParameter parameter = new ScanParameter();
        parameter.setProjectName(DEV_PROJECT_NAME);
        parameter.setScanPath(Path.of(scanPath));
        parameter.setOutputDir(Path.of(outDir));

        ScanResult result = scanEngine.scan(parameter);
        ReportJson report = reportParser.parse(result.getJsonReport().toFile());
        Long taskId = scanPersistService.persist(DEV_PROJECT_ID, report, result.getDurationMs());

        LOG.info("[DEV] deps={} durationMs={} taskId={} html={}",
                result.getDependencyCount(), result.getDurationMs(), taskId, result.getHtmlReport());
    }
}
