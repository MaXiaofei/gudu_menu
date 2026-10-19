package com.gudu.xsd.modules.community.infrastructure;

import com.gudu.xsd.modules.community.domain.service.PostModerator;
import com.gudu.xsd.modules.community.domain.service.PostTextWriter;
import com.gudu.xsd.modules.community.domain.service.RulePostModerator;
import com.gudu.xsd.modules.community.domain.service.TemplatePostTextWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 纯领域服务装配：默认规则预审 + 模板文案；接入模型网关后可提供同类型 Bean 覆盖。 */
@Configuration
public class CommunityDomainConfig {

    @Bean
    @ConditionalOnMissingBean(PostModerator.class)
    public PostModerator postModerator() {
        return new RulePostModerator();
    }

    @Bean
    @ConditionalOnMissingBean(PostTextWriter.class)
    public PostTextWriter postTextWriter() {
        return new TemplatePostTextWriter();
    }
}
