package com.company.depcheck.engine.report;

import com.company.depcheck.common.exception.BusinessException;
import com.company.depcheck.common.result.ResultCode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 把 DC 的 JSON 报告读成 DTO。
 *
 * @author depcheck
 * @since 1.0.0
 */
@Component
public class ReportParser {

    private final ObjectMapper objectMapper;

    /**
     * 复用 Spring 的 ObjectMapper，复制一份后放开未知字段。
     *
     * @param objectMapper Spring 容器里的 ObjectMapper
     */
    public ReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 解析报告文件。
     *
     * @param reportFile dependency-check-report.json
     * @return 报告对象
     * @throws BusinessException 文件不存在或解析失败
     */
    public ReportJson parse(File reportFile) {
        if (reportFile == null || !reportFile.exists()) {
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "报告文件不存在");
        }
        try {
            return objectMapper.readValue(reportFile, ReportJson.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SCAN_FAILED.getCode(), "报告解析失败：" + e.getMessage());
        }
    }
}
