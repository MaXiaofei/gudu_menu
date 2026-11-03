package com.gudu.xsd.modules.ops.infrastructure;

import com.gudu.xsd.modules.ai.AiClientRouter;
import com.gudu.xsd.modules.ops.domain.service.HealthProbe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;

/** 健康探测实现：应用自身 / MySQL / Redis / PGVector / AI。 */
@Configuration
public class HealthProbes {

    @Bean
    public HealthProbe appProbe() {
        return new Simple("app", "应用服务", () -> {
            String ver = HealthProbes.class.getPackage().getImplementationVersion();
            Duration up = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
            return "版本 " + (ver == null ? "开发版" : ver) + " · 已运行 " + formatUptime(up);
        });
    }

    @Bean
    public HealthProbe mysqlProbe(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new Simple("mysql", "MySQL", () -> {
            jdbc.queryForObject("select 1", Integer.class);
            return "查询正常";
        });
    }

    @Bean
    public HealthProbe redisProbe(StringRedisTemplate redis) {
        return new Simple("redis", "Redis", () -> {
            String pong = redis.execute((RedisCallback<String>) RedisConnection::ping);
            if (pong == null) throw new IllegalStateException("无响应");
            return "连通正常";
        });
    }

    @Bean
    public HealthProbe vectorProbe(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate pgJdbc) {
        return new Simple("vector", "语义搜索", () -> {
            pgJdbc.queryForObject("select 1", Integer.class);
            return "向量库查询正常";
        });
    }

    @Bean
    public HealthProbe aiProbe(AiClientRouter router) {
        return new Simple("ai", "AI 服务", () -> {
            String provider = router.currentProvider();
            if (!router.providerReady(provider)) throw new IllegalStateException("当前模型 " + provider + " 未配置密钥");
            return "当前模型 " + provider;
        });
    }

    static String formatUptime(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) return days + " 天 " + hours + " 小时";
        if (hours > 0) return hours + " 小时 " + minutes + " 分";
        return minutes + " 分钟";
    }

    @FunctionalInterface
    interface Check {
        String run() throws Exception;
    }

    record Simple(String key, String label, Check body) implements HealthProbe {
        @Override
        public String check() throws Exception {
            return body.run();
        }
    }
}
