package com.gudu.xsd.modules.archfixture.infrastructure;

import com.gudu.xsd.modules.archfixture.interfaces.FixtureFacade;

/** ArchUnit 反例夹具：基础设施反向依赖接口层。 */
public class FixtureRepoImpl {
    public Object facade() {
        return new FixtureFacade();
    }
}
