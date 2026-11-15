package com.company.depcheck.controller;

import com.company.depcheck.common.result.Result;
import com.company.depcheck.engine.parity.ParityChecker;
import com.company.depcheck.engine.parity.ParityResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * 一致性自检接口（仅 dev.parity-enabled=true 时暴露）。
 *
 * <p>GET /api/dev/parity?path=D:\dc\samples\spring-petclinic
 * 分别用官方 CLI 与进程内引擎扫同一工程，返回依赖/漏洞差异。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "dev.parity-enabled", havingValue = "true")
public class DevParityController {

    private final ParityChecker parityChecker;

    /**
     * 构造注入。
     *
     * @param parityChecker 一致性检查器
     */
    public DevParityController(ParityChecker parityChecker) {
        this.parityChecker = parityChecker;
    }

    /**
     * 执行一致性比对。
     *
     * @param path 待扫描工程路径
     * @return 比对结果
     */
    @GetMapping("/parity")
    public Result<ParityResult> parity(@RequestParam String path) {
        return Result.ok(parityChecker.check(Path.of(path)));
    }
}
