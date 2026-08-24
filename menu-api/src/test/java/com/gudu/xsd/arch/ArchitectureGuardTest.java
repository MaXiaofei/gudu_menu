package com.gudu.xsd.arch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD 分层与模块边界守护（ArchUnit）。
 *
 * <p>项目为「按功能分包」结构：modules.&lt;feature&gt; 内 Controller/Service/Mapper 混放，
 * 每个 module 视为一个限界上下文（Bounded Context）雏形。守护目标：
 * <ol>
 *   <li>接口层（Controller）不越权访问持久层；</li>
 *   <li>Mapper 作为模块私有数据通道，跨模块只能走对方 Service（应用层契约）；</li>
 *   <li>实体（@TableName）保持领域纯度，不反向依赖上层；</li>
 *   <li>common/config 公共层不反向依赖业务模块。</li>
 * </ol>
 *
 * <p>「跨模块 Mapper 访问」存量违规通过 {@link FreezingArchRule} 冻结基线：只减不增，
 * 基线文件见 src/test/resources/archunit_store/，清零后可删除冻结包装。
 *
 * <p>实现说明：规则用静态字段声明，由 {@code #守护()} 用 Jupiter 原生 @Test 逐条执行——
 * 规避 surefire 对 ArchUnit 自带引擎发现的字段级测试计数为 0 的问题。
 */
class ArchitectureGuardTest {

    private static JavaClasses importClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.gudu.xsd");
    }

    @Test
    void 守护() {
        JavaClasses classes = importClasses();
        List<String> failures = new ArrayList<>();
        for (ArchRule rule : List.of(controller不得依赖Mapper, controller不得依赖数据访问设施, mapper包规范,
                mapper命名收口, 实体不依赖上层, 公共层不依赖业务模块, 跨模块Mapper访问冻结)) {
            rule.evaluate(classes).getFailureReport().getDetails()
                    .forEach(detail -> failures.add("[" + rule.getDescription() + "]\n  " + detail));
        }
        org.assertj.core.api.Assertions.assertThat(failures)
                .as("架构守护规则全部通过（阿里规约/DDD 分层），违规明细如下")
                .isEmpty();
    }


    private static final String MODULES_ROOT = "com.gudu.xsd.modules.";

    // ------------------------------------------------------------------
    // 接口层职责
    // ------------------------------------------------------------------

    /** Controller 不得依赖任何 Mapper——查询逻辑必须下沉到模块内 Service。 */
    static final ArchRule controller不得依赖Mapper = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("com.gudu.xsd.modules..mapper..");

    /** Controller 不得依赖 ORM/ JDBC 基础设施（IPage 等展示用元数据除外）。 */
    static final ArchRule controller不得依赖数据访问设施 = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.baomidou.mybatisplus.core.mapper..", "java.sql..");

    // ------------------------------------------------------------------
    // 持久层规范
    // ------------------------------------------------------------------

    /** mapper 包内的类型必须是继承 BaseMapper 且以 Mapper 结尾的接口。 */
    static final ArchRule mapper包规范 = classes()
            .that().resideInAPackage("com.gudu.xsd.modules..mapper..")
            .should().beInterfaces()
            .andShould().beAssignableTo(BaseMapper.class)
            .andShould().haveSimpleNameEndingWith("Mapper");

    /** 以 Mapper 结尾的类必须收口在 mapper 包内。 */
    static final ArchRule mapper命名收口 = classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .should().resideInAPackage("com.gudu.xsd.modules..mapper..");

    // ------------------------------------------------------------------
    // 领域纯度与公共层方向
    // ------------------------------------------------------------------

    /** MyBatis-Plus 实体（数据模型）不得依赖 Controller/Service/Mapper。 */
    static final ArchRule 实体不依赖上层 = noClasses()
            .that().areAnnotatedWith("com.baomidou.mybatisplus.annotation.TableName")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Service")
            .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Mapper");

    /** 公共层不得反向依赖业务模块（依赖方向必须自上而下）。 */
    static final ArchRule 公共层不依赖业务模块 = noClasses()
            .that().resideInAnyPackage("com.gudu.xsd.common..", "com.gudu.xsd.config..")
            .should().dependOnClassesThat().resideInAPackage("com.gudu.xsd.modules..");

    // ------------------------------------------------------------------
    // 模块边界（存量违规冻结，只减不增）
    // ------------------------------------------------------------------

    /**
     * 跨模块直接访问他模块 Mapper：冻结规则。
     * module 视为限界上下文：shopping 模块的 Service 不得绕过 dish 模块直接摸 dish 表，
     * 应改为调用 DishService 暴露的应用层方法（防腐）。
     * 存量违规见 archunit_store 基线，整改后在基线文件中同步删除对应行。
     */
    static final ArchRule 跨模块Mapper访问冻结 = FreezingArchRule.freeze(noClasses()
            .that().resideInAPackage("com.gudu.xsd.modules..")
            .should(new ArchCondition<>("只访问本模块的 Mapper（跨模块数据访问必须走对方模块 Service）") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    String module = topModuleOf(clazz);
                    for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                        JavaClass target = dep.getTargetClass();
                        if (isModuleMapper(target) && !topModuleOf(target).equals(module)) {
                            events.add(SimpleConditionEvent.satisfied(dep,
                                    dep.getDescription() + " 跨模块访问了 ["
                                            + topModuleOf(target) + "] 模块的 Mapper，应改走对方 Service"));
                        }
                    }
                }
            }));

    /** modules.&lt;feature&gt;.… 取顶层 feature（menu.prep / menu.together 归并为 menu）。 */
    private static String topModuleOf(JavaClass c) {
        String pkg = c.getPackageName();
        if (!pkg.startsWith(MODULES_ROOT)) {
            return "";
        }
        String rest = pkg.substring(MODULES_ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** 本项目惯例：Mapper 接口一律位于 modules.&lt;feature&gt;.mapper 包。 */
    private static boolean isModuleMapper(JavaClass c) {
        String pkg = c.getPackageName();
        return pkg.startsWith(MODULES_ROOT) && pkg.endsWith(".mapper");
    }
}
