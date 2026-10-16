package com.gudu.xsd.arch;

import com.gudu.xsd.modules.archfixture.application.LeakyAppService;
import com.gudu.xsd.modules.archfixture.domain.event.NotAnEvent;
import com.gudu.xsd.modules.archfixture.domain.model.MutableThing;
import com.gudu.xsd.modules.archfixture.domain.service.SpringyService;
import com.gudu.xsd.modules.archfixture.infrastructure.FixtureRepoImpl;
import com.gudu.xsd.modules.archfixture.interfaces.FixtureFacade;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD 四层守护规则自检：对反例夹具（test 源 modules.archfixture）逐条求值，必须报违规——
 * 防止规则写错成「永远通过」（ArchUnit noClasses 极性翻转坑见 backend-ddd-architecture/design.md §5）。
 */
class DddRulesSelfTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter().importClasses(
            MutableThing.class, NotAnEvent.class, SpringyService.class, LeakyAppService.class,
            FixtureRepoImpl.class, FixtureFacade.class);

    private static void assertViolates(ArchRule rule) {
        assertThat(rule.evaluate(FIXTURES).hasViolation())
                .as("规则应对反例报违规: " + rule.getDescription()).isTrue();
    }

    @Test
    void 每条四层规则都能抓到反例() {
        assertViolates(ArchitectureGuardTest.领域层零框架依赖);
        assertViolates(ArchitectureGuardTest.领域模型禁public_setter);
        assertViolates(ArchitectureGuardTest.领域事件必须实现DomainEvent);
        assertViolates(ArchitectureGuardTest.领域模型实体必须是聚合根);
        assertViolates(ArchitectureGuardTest.领域层不依赖外层);
        assertViolates(ArchitectureGuardTest.应用层不依赖接口层与基础设施);
        assertViolates(ArchitectureGuardTest.基础设施不依赖应用层与接口层);
        assertViolates(ArchitectureGuardTest.接口层不依赖基础设施);
    }
}
