package com.ruoyi.architecture;

import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.service.ISysProductService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.BaseEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构约束结构化测试（ArchUnit）
 *
 * 把 docs/architecture/boundaries.md 中的模块边界铁律编码成可执行断言。
 * 违反规则时 CI 直接失败，错误信息包含「是什么问题 / 为什么 / 怎么修 / 去哪看」四要素，
 * 便于 AI Agent 自主修复。
 *
 * 运行：mvn test -pl ruoyi-admin -Dtest=ArchitectureRulesTest
 *
 * @author life
 * @date 2026-08-30
 */
@AnalyzeClasses(packages = "com.ruoyi", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest
{
    private static final String DOC = "详见 docs/architecture/boundaries.md";

    /**
     * 铁律 4：ruoyi-common 必须零业务依赖
     */
    @ArchTest
    static final ArchRule commonModuleMustNotDependOnOtherModules = noClasses()
        .that().resideInAPackage("com.ruoyi.common..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.ruoyi.system..", "com.ruoyi.framework..", "com.ruoyi.quartz..",
            "com.ruoyi.generator..", "com.ruoyi.web..", "com.ruoyi.biz..")
        .because("铁律4：ruoyi-common 是依赖树最底层，必须保持无状态、无业务。"
            + "违反说明业务逻辑泄漏到了公共层，会影响全部模块。"
            + "怎么修：把业务代码移回 com.ruoyi.biz 或 com.ruoyi.system，"
            + "common 只保留无状态工具、常量、枚举、注解、异常与通用基类。" + DOC);

    /**
     * 铁律 3：Controller 只允许出现在启动模块与定时任务模块
     */
    @ArchTest
    static final ArchRule controllersMustResideInAllowedModules = classes()
        .that().haveSimpleNameEndingWith("Controller")
        .and().doNotHaveSimpleName("BaseController")
        .should().resideInAnyPackage(
            "com.ruoyi.web.controller..",         // ruoyi-admin：系统管理 / 监控 / 通用
            "com.ruoyi.biz.controller..",         // ruoyi-admin：自有业务
            "com.ruoyi.quartz.controller..",      // ruoyi-quartz：模块自带（框架遗留，不改造）
            "com.ruoyi.generator.controller..")   // ruoyi-generator：模块自带（框架遗留，不改造）
        .because("铁律3：Controller 属于表现层，只允许出现在 ruoyi-admin"
            + "（com.ruoyi.web.controller 与 com.ruoyi.biz.controller）、"
            + "ruoyi-quartz、ruoyi-generator 三处。"
            + "放进 ruoyi-system / ruoyi-framework / ruoyi-common 会让领域层反向依赖 Web 层。"
            + "怎么修：把 Controller 移到 ruoyi-admin/src/main/java/com/ruoyi/biz/controller/ 下。"
            + "注意：ruoyi-generator 与 ruoyi-quartz 自带 Controller 属 RuoYi 原生设计，"
            + "已在白名单内，不要为了消除告警去改动框架代码。" + DOC);

    /**
     * 铁律：业务 Controller 的公开方法必须有权限注解（防止越权漏洞）
     */
    @ArchTest
    static final ArchRule bizControllerMethodsMustBeSecured = methods()
        .that().areDeclaredInClassesThat().resideInAPackage("com.ruoyi.biz.controller..")
        .and().arePublic()
        .should().beAnnotatedWith(PreAuthorize.class)
        .because("安全红线：com.ruoyi.biz 下的每个 Controller 公开方法都必须带 "
            + "@PreAuthorize(\"@ss.hasPermi('模块:功能:操作')\")。"
            + "漏注解 = 真实越权漏洞（前端隐藏按钮不算防护，接口可被直接调用）。"
            + "怎么修：补 @PreAuthorize，权限串需与 sys_menu.perms 及前端 v-hasPermi 完全一致。"
            + DOC);

    /**
     * 分层规则：Controller 不得跨过 Service 直接依赖 Mapper
     */
    @ArchTest
    static final ArchRule controllersMustNotDependOnMappers = noClasses()
        .that().haveSimpleNameEndingWith("Controller")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
        .because("分层规则：Controller 只能依赖 Service 接口，不得直接注入 Mapper，"
            + "否则事务边界与业务规则会被绕过。"
            + "怎么修：在 IXxxService 中新增方法，Controller 改为调用 Service。" + DOC);

    /**
     * 命名规范：业务 Service 接口必须以 I 开头
     */
    @ArchTest
    static final ArchRule bizServiceInterfacesMustBePrefixedWithI = classes()
        .that().resideInAPackage("com.ruoyi.biz.service..")
        .and().areInterfaces()
        .should().haveSimpleNameStartingWith("I")
        .because("命名规范：com.ruoyi.biz 下的 Service 接口必须以 I 开头（如 ISysProductService），"
            + "实现类以 Impl 结尾。与 RuoYi 代码生成器输出保持一致。"
            + "怎么修：重命名为 I + 实体名 + Service。" + DOC);

    /**
     * 命名规范：Service 实现类必须以 Impl 结尾
     */
    @ArchTest
    static final ArchRule bizServiceImplMustBeSuffixedWithImpl = classes()
        .that().resideInAPackage("com.ruoyi.biz.service.impl..")
        .should().haveSimpleNameEndingWith("ServiceImpl")
        .because("命名规范：com.ruoyi.biz.service.impl 下的实现类必须以 ServiceImpl 结尾。"
            + "怎么修：重命名为 XxxServiceImpl。" + DOC);

    /**
     * 分层落位：Mapper 接口必须在 mapper 包下
     */
    @ArchTest
    static final ArchRule mappersMustResideInMapperPackage = classes()
        .that().haveSimpleNameEndingWith("Mapper")
        .should().resideInAPackage("..mapper..")
        .because("分层落位：Mapper 接口必须放在 ..mapper.. 包下，"
            + "否则 MyBatis 的 @MapperScan 扫不到，启动报 Bean 不存在。"
            + "怎么修：移到 com.ruoyi.biz.mapper（位于 ruoyi-system 模块）。" + DOC);

    /**
     * 分层落位：Service 实现必须在 service.impl 包下
     */
    @ArchTest
    static final ArchRule serviceImplsMustResideInServiceImplPackage = classes()
        .that().haveSimpleNameEndingWith("ServiceImpl")
        .and().resideInAPackage("com.ruoyi.biz..")
        .should().resideInAPackage("..service.impl..")
        .because("分层落位：com.ruoyi.biz 下的 Service 实现类必须放在 ..service.impl.. 包下。"
            + "怎么修：移到 com.ruoyi.biz.service.impl（位于 ruoyi-system 模块）。"
            + "注意：本规则只约束 com.ruoyi.biz —— "
            + "ruoyi-framework 的 UserDetailsServiceImpl、ruoyi-generator 的 GenTableServiceImpl "
            + "位于各自的 service 包下，属 RuoYi 原生结构，不在管辖范围内，不要去改它们。" + DOC);

    /**
     * 铁律 1 的前置保障：业务实体必须继承 BaseEntity 或 TreeEntity
     */
    @ArchTest
    static final ArchRule bizEntitiesMustExtendBaseEntity = classes()
        .that().resideInAPackage("com.ruoyi.biz.domain..")
        .should().beAssignableTo(BaseEntity.class)
        .because("铁律1前置：com.ruoyi.biz.domain 下的实体必须继承 BaseEntity（普通表）"
            + "或 TreeEntity（树形表），脚手架生成的 Mapper XML 依赖 "
            + "create_by/create_time/update_by/update_time/remark 这些审计列。"
            + "不继承会导致审计字段无法回填、数据权限注解失效。"
            + "怎么修：改为 extends BaseEntity 或 extends TreeEntity。"
            + DOC + " 与 docs/conventions/README.md#26-实体类规范");

    /**
     * 风格红线：禁止引入 Lombok
     */
    @ArchTest
    static final ArchRule noLombok = noClasses()
        .should().dependOnClassesThat().resideInAPackage("lombok..")
        .because("风格红线：本仓库未引入 Lombok，所有 getter/setter 手写（与 RuoYi 代码生成器一致）。"
            + "引入 Lombok 会造成同一模块内两种风格并存，且后续生成代码会被覆盖回手写形式。"
            + "怎么修：删除 @Data/@Getter 等注解，手写 getter/setter 与 serialVersionUID。"
            + DOC);

    /**
     * 模块切片不得出现循环依赖
     */
    @ArchTest
    static final ArchRule modulesMustBeFreeOfCycles = slices()
        .matching("com.ruoyi.(*)..")
        .should().beFreeOfCycles()
        .because("依赖规则：com.ruoyi 各顶层包之间只能自上而下依赖，禁止成环。"
            + "成环会导致 Maven 构建顺序无法确定，且使模块无法独立拆分。"
            + "怎么修：把双方共用的代码下沉到 com.ruoyi.common，"
            + "或引入接口让高层依赖抽象。" + DOC);

    /**
     * 自我保护：确认 ArchUnit 真的扫到了各个模块
     *
     * 如果 classpath 上缺少某个模块的类，上面的规则会「空跑通过」，
     * 给人虚假的安全感。这个用例就是为了防止这种情况。
     */
    @Test
    @DisplayName("自检：ArchUnit 必须能扫到全部模块的类")
    void importedClassesShouldCoverAllModules()
    {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ruoyi");

        assertThat(classes).isNotEmpty();
        assertThat(classes.contain(BaseController.class))
            .as("classpath 缺少 ruoyi-common 的类，规则会空跑通过").isTrue();
        assertThat(classes.contain(SysProduct.class))
            .as("classpath 缺少 ruoyi-system 的 biz.domain 类，规则会空跑通过").isTrue();
        assertThat(classes.contain(ISysProductService.class))
            .as("classpath 缺少 ruoyi-system 的 biz.service 类，规则会空跑通过").isTrue();
    }
}
