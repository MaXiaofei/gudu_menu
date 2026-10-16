package com.gudu.xsd.modules.archfixture.application;

import com.gudu.xsd.modules.archfixture.infrastructure.FixtureRepoImpl;

/** ArchUnit 反例夹具：应用层直连基础设施实现。 */
public class LeakyAppService {
    public Object repo() {
        return new FixtureRepoImpl();
    }
}
