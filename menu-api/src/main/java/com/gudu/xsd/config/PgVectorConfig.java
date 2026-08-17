package com.gudu.xsd.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PgVector 向量库接线修复（staging 启动失败根因，2026-08-16）。
 *
 * <p>问题：Spring AI 的 PgVectorStore 自动配置消费的是<b>主数据源（MySQL）</b>，
 * application.yml 里的 spring.ai.vectorstore.pgvector.host/port/... 并不被自动配置读取——
 * 启动时 CREATE EXTENSION 被发给 MySQL → bad SQL grammar → vectorStore→dishService→aiService
 * 整条 bean 链断裂，应用无法启动。
 *
 * <p>修复：显式定义指向 PostgreSQL 的独立数据源 + JdbcTemplate，并自建 vectorStore bean
 * （自动配置 @ConditionalOnMissingBean 自动退让）。参数沿用 application.yml 既有键
 * （PGVECTOR_HOST/PORT/PASSWORD 环境变量），维度/距离/索引与原配置一致（bge-m3 / 1024 / COSINE / HNSW）。
 */
@Configuration
public class PgVectorConfig {

    /**
     * 显式声明 MySQL 主数据源（@Primary）：本类同时定义了 PG 向量库数据源，
     * 一旦容器出现第二个 DataSource，Spring Boot 的 DataSourceAutoConfiguration
     * 会因 @ConditionalOnMissingBean 整体退让——不显式建主库，业务 SQL 全打到 PG
     * （2026-08-17 staging 登录 500 根因：relation "member" does not exist）。
     * 属性沿用 spring.datasource.*（各 profile 的 MySQL 连接）。
     */
    @Bean
    @Primary
    public DataSource dataSource(
            org.springframework.boot.autoconfigure.jdbc.DataSourceProperties props) {
        // DataSourceProperties 桥接 spring.datasource.* → Hikari（url→jdbcUrl 等属性名映射）
        return props.initializeDataSourceBuilder().build();
    }

    @Bean
    public DataSource pgVectorDataSource(
            @Value("${spring.ai.vectorstore.pgvector.host:127.0.0.1}") String host,
            @Value("${spring.ai.vectorstore.pgvector.port:5432}") int port,
            @Value("${spring.ai.vectorstore.pgvector.database:gudu}") String database,
            @Value("${spring.ai.vectorstore.pgvector.username:gudu}") String username,
            @Value("${spring.ai.vectorstore.pgvector.password:gudu_vec_2026}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://%s:%d/%s".formatted(host, port, database));
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(4);
        ds.setPoolName("pgvector-pool");
        return ds;
    }

    @Bean
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 覆盖自动配置的 vectorStore（其误用主 MySQL 数据源）。
     * initializeSchema=true：启动自动建 vector_store 表（需 pgvector 扩展已建，
     * 由部署脚本幂等执行 CREATE EXTENSION IF NOT EXISTS vector）。
     */
    @Bean
    public VectorStore vectorStore(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024) // bge-m3 输出维度
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
