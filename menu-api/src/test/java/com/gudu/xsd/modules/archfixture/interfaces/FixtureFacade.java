package com.gudu.xsd.modules.archfixture.interfaces;

import com.gudu.xsd.modules.archfixture.infrastructure.FixtureRepoImpl;

/** ArchUnit 反例夹具：接口层直连基础设施。 */
public class FixtureFacade {
    public Object repo() {
        return FixtureRepoImpl.class;
    }
}
