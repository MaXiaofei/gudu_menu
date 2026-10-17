package com.gudu.xsd.modules.agent.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 咕嘟后台线程池：SSE 对话（请求线程立即返回 emitter）与评测运行。 */
@Configuration
public class AgentExecutorConfig {

    @Bean("agentExecutor")
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("agent-");
        ex.initialize();
        return ex;
    }
}
