package com.gudu.xsd.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：租户插件（家庭隔离，multi-tenant-family）+ 分页插件。
 * 顺序：租户在前（先改写 SQL 加 family_id，再由分页生成 count/limit）。
 * 单页上限与 PageQuery 一致，防止单请求拖全表。
 */
@Configuration
public class MybatisPlusConfig {

    static final long MAX_PAGE_SIZE = com.gudu.xsd.common.PageQuery.MAX_PAGE_SIZE;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new GuduTenantLineInnerInterceptor());
        interceptor.addInnerInterceptor(new DishVisibilityInnerInterceptor());
        interceptor.addInnerInterceptor(new IngredientVisibilityInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
