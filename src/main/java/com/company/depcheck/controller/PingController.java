package com.company.depcheck.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.depcheck.common.log.TraceIdFilter;
import com.company.depcheck.common.result.Result;

/**
 * 健康检查接口：验证"能起、能返回统一结构、日志带 traceId"。
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api")
public class PingController {

    private static final Logger LOG = LoggerFactory.getLogger(PingController.class);

    private static final String APP_NAME = "depcheck";

    /**
     * 健康检查。
     *
     * @return 应用名、当前时间与 traceId
     */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>(4);
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        data.put("app", APP_NAME);
        data.put("time", LocalDateTime.now().toString());
        data.put("traceId", traceId);
        LOG.info("ping, traceId={}", traceId);
        return Result.ok(data);
    }
}
