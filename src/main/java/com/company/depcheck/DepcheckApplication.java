package com.company.depcheck;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口。
 *
 * @author depcheck
 * @since 1.0.0
 */
@SpringBootApplication
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
