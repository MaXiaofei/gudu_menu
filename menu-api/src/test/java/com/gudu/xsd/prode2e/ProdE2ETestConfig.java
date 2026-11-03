package com.gudu.xsd.prode2e;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * 生产端到端测试的上下文调整：本机进程连着生产库，绝不能跑 {@code @Scheduled} 定时任务
 * （全站临期通知、保存期限清理、识别任务清理、计数回刷会对真实用户 / 真实数据动手）。
 * 做法：摘掉 {@code @Scheduled} 注解处理器的定义，定时方法就永远不会被注册。
 */
@TestConfiguration(proxyBeanMethods = false)
public class ProdE2ETestConfig {

    @Bean
    static BeanDefinitionRegistryPostProcessor disableScheduling() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                String name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
                if (registry.containsBeanDefinition(name)) {
                    BeanDefinition ignored = registry.getBeanDefinition(name);
                    registry.removeBeanDefinition(name);
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                // 无需处理
            }
        };
    }
}
