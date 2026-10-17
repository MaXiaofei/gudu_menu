package com.gudu.xsd.modules.ai.gateway.infrastructure;

import com.gudu.xsd.common.domain.DomainEventPublisher;
import com.gudu.xsd.modules.ai.gateway.domain.event.GatewayConfigChangedEvent;
import com.gudu.xsd.modules.ai.gateway.domain.port.GatewayChangeBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 网关配置热切换：变更后向频道 {@value #CHANNEL} 发布；每个实例订阅该频道，收到即发布
 * {@link GatewayConfigChangedEvent}，由 ModelGateway 失效本地缓存。Redis 不可用时各实例在缓存 TTL（60s）后自然生效。
 */
@Slf4j
@Configuration
public class RedisGatewayChangeBus implements GatewayChangeBus {

    public static final String CHANNEL = "gudu:ai:gateway:changed";

    private final StringRedisTemplate redis;

    public RedisGatewayChangeBus(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void publishChanged(String reason) {
        redis.convertAndSend(CHANNEL, reason == null ? "changed" : reason);
    }

    /** 订阅容器：收到广播 → 发布进程内事件。 */
    @Bean
    public RedisMessageListenerContainer gatewayChangeListenerContainer(RedisConnectionFactory factory,
                                                                        DomainEventPublisher publisher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        MessageListener listener = (message, pattern) -> {
            String reason = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                publisher.publish(new GatewayConfigChangedEvent("redis:" + reason));
            } catch (RuntimeException e) {
                log.warn("处理网关变更广播失败: {}", e.getMessage());
            }
        };
        container.addMessageListener(listener, new ChannelTopic(CHANNEL));
        return container;
    }
}
