package com.gudu.xsd.arch;

import com.gudu.xsd.common.R;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 《阿里巴巴 Java 开发手册》可自动化条款守护（ArchUnit）。
 *
 * <p>只收录能用字节码分析可靠判定的【强制】级条款，其余条款走 Code Review。
 * 条款出处以「手册 v1.7.x 章节」标注在每条规则上方。
 *
 * <p>实现说明：规则用静态字段声明，由 {@code #守护()} 用 Jupiter 原生 @Test 逐条执行——
 * 规避 surefire 对 ArchUnit 自带引擎发现的字段级测试计数为 0 的问题。
 */
class AlibabaStandardGuardTest {

    private static JavaClasses importClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.gudu.xsd");
    }

    @Test
    void 守护() {
        JavaClasses classes = importClasses();
        List<String> failures = new ArrayList<>();
        for (ArchRule rule : List.of(禁止printStackTrace, 禁止标准流输出, 方法事务必须指定rollbackFor, 类事务必须指定rollbackFor,
                禁止字段注入, 禁止遗留日期API, web层命名规范, controller统一返回R)) {
            rule.evaluate(classes).getFailureReport().getDetails()
                    .forEach(detail -> failures.add("[" + rule.getDescription() + "]\n  " + detail));
        }
        org.assertj.core.api.Assertions.assertThat(failures)
                .as("架构守护规则全部通过（阿里规约/DDD 分层），违规明细如下")
                .isEmpty();
    }


    // ------------------------------------------------------------------
    // 【强制】异常处理：禁用控制台输出，统一走日志框架
    // ------------------------------------------------------------------

    /** 手册「日志规约」：应用中不可直接使用日志系统（Log4j、Logback）的 API，更不得用 printStackTrace。 */
    static final ArchRule 禁止printStackTrace = classes()
            .should(new ArchCondition<JavaClass>("用日志框架（log.error）输出异常，禁止 printStackTrace") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
                        // 字节码里 owner 是静态类型（如 Exception，继承自 Throwable），按可赋值性判断
                        if ("printStackTrace".equals(call.getTarget().getName())
                                && call.getTargetOwner().isAssignableTo(Throwable.class)) {
                            events.add(SimpleConditionEvent.violated(call,
                                    call.getDescription() + " 使用了 printStackTrace，应改 log.error(\"...\", e)"));
                        }
                    }
                }
            });

    /** 手册「OOP 规约/日志」：生产代码禁止 System.out / System.err（单元测试除外，本测试已排除测试类）。 */
    static final ArchRule 禁止标准流输出 = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    // ------------------------------------------------------------------
    // 【强制】事务：@Transactional 必须显式 rollbackFor
    // ------------------------------------------------------------------

    /** 手册「事务场景」：必须 rollbackFor = Exception.class（默认仅回滚 RuntimeException，受检异常会漏回滚）。 */
    static final ArchRule 方法事务必须指定rollbackFor = methods()
            .that().areAnnotatedWith(Transactional.class)
            .should(new ArchCondition<JavaMethod>("显式声明 rollbackFor") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    JavaAnnotation<?> anno = method.getAnnotationOfType(Transactional.class.getName());
                    if (anno == null || !declaresRollbackFor(anno)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getDescription() + " 未声明 rollbackFor（默认仅回滚 RuntimeException）"));
                    }
                }
            });

    /** 类级 @Transactional 同样要求 rollbackFor。 */
    static final ArchRule 类事务必须指定rollbackFor = classes()
            .that().areAnnotatedWith(Transactional.class)
            .should(new ArchCondition<JavaClass>("显式声明 rollbackFor") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    JavaAnnotation<?> anno = clazz.getAnnotationOfType(Transactional.class.getName());
                    if (anno == null || !declaresRollbackFor(anno)) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getDescription() + " 类级 @Transactional 未声明 rollbackFor"));
                    }
                }
            })
            .allowEmptyShould(true);

    /**
     * 判断 @Transactional 是否显式声明了 rollbackFor。
     * 注意：ArchUnit 的 getProperties() 会并入注解默认值，未声明时 rollbackFor 为空数组，
     * 必须按「数组非空」判断，containsKey 恒为 true。
     */
    private static boolean declaresRollbackFor(JavaAnnotation<?> anno) {
        Object rollbackFor = anno.getProperties().get("rollbackFor");
        return rollbackFor != null && rollbackFor.getClass().isArray()
                && java.lang.reflect.Array.getLength(rollbackFor) > 0;
    }

    // ------------------------------------------------------------------
    // 【强制】OOP：构造器注入，禁止字段注入
    // ------------------------------------------------------------------

    /** 手册「OOP 规约」：推荐构造器注入（项目用 @RequiredArgsConstructor），禁止 @Autowired 字段注入。 */
    static final ArchRule 禁止字段注入 = noClasses()
            .should(new ArchCondition<JavaClass>("使用构造器注入替代 @Autowired 字段注入") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    for (JavaField field : clazz.getFields()) {
                        if (field.isAnnotatedWith(Autowired.class)) {
                            // noClasses() 下自定义条件用 satisfied 上报（ArchUnit 翻转为失败）
                            events.add(SimpleConditionEvent.satisfied(field,
                                    field.getDescription() + " 使用了 @Autowired 字段注入（应构造器注入）"));
                        }
                    }
                }
            });

    // ------------------------------------------------------------------
    // 【强制】日期时间：禁用 java.util.Date / SimpleDateFormat
    // ------------------------------------------------------------------

    /** 手册「集合/并发/日期」：SimpleDateFormat 线程不安全；日期一律用 JSR-310（LocalDateTime 等）。 */
    static final ArchRule 禁止遗留日期API = noClasses()
            .should().dependOnClassesThat().belongToAnyOf(Date.class, java.text.SimpleDateFormat.class);

    // ------------------------------------------------------------------
    // 【强制】分层与命名：Web 层规约
    // ------------------------------------------------------------------

    /** 手册「分层领域模型」：Web 层类名以 Controller 结尾，且收口在业务模块内。 */
    static final ArchRule web层命名规范 = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller")
            .andShould().resideInAPackage("com.gudu.xsd.modules..");

    /** 项目约定（手册「接口规约」延伸）：Controller 出口统一返回 R&lt;T&gt;，前端按同一结构解包。 */
    static final ArchRule controller统一返回R = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            // 豁免：微信服务器地址验证接口须原样回显纯文本 echostr（微信协议要求，不能包 R）
            .and().doNotHaveName("wxEcho")
            .should(new ArchCondition<JavaMethod>("返回统一响应 com.gudu.xsd.common.R") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (!R.class.getName().equals(method.getRawReturnType().getName())) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getDescription() + " 返回了 " + method.getRawReturnType().getName()));
                    }
                }
            });
}
