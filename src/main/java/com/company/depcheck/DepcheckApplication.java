package com.company.depcheck;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口。
 *
 * <p>{@code @EnableScheduling} 是超时看门狗（{@code ScanTimeoutWatchdog}）生效的前提，
 * 少了这个注解 {@code @Scheduled} 会被静默忽略。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.company.depcheck.mapper")
public class DepcheckApplication {

    /**
     * 程序启动主方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DepcheckApplication.class, args);
    }
}
