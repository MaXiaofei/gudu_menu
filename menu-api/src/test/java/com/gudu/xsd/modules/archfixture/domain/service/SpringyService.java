package com.gudu.xsd.modules.archfixture.domain.service;

import com.gudu.xsd.modules.archfixture.application.LeakyAppService;

/** ArchUnit 反例夹具：领域层依赖 Spring 与应用层。 */
public class SpringyService {
    public boolean blank(String s) {
        return !org.springframework.util.StringUtils.hasText(s) && new LeakyAppService() != null;
    }
}
