package com.company.depcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * MyBatis-Plus 插件：分页。
 *
 * <p>结果页要按页取依赖与漏洞，没有分页插件就只能全表捞。分页插件是 MP 自带模块，
 * 不属于"额外组件"。</p>
 *
 * @author depcheck
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器。
     *
     * @return MP 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.H2);
        // 单页上限，防止前端传 size=100000 把库拖死
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
