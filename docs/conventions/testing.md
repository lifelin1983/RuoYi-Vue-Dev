# 测试规范

> **现状：本仓库已有 56 个用例且全绿**（架构 15 + 业务 41），2 个 skipped 是 `@Disabled` 固化的已知缺陷，不是失败。
> 本文档既是规范，也是**从 0 到 1 的落地路径记录**——架构测试、Service、Mapper 三批已完成，Controller 测试待补。
> 相关文档：[编码规范](./README.md) ｜ [模块边界](../architecture/boundaries.md)

---

## 目录

1. [现状与目标](#1-现状与目标)
2. [测试金字塔与选型](#2-测试金字塔与选型)
3. [依赖接入（第一步）](#3-依赖接入第一步)
4. [目录与命名](#4-目录与命名)
5. [单元测试规范](#5-单元测试规范)
6. [Mapper 测试规范](#6-mapper-测试规范)
7. [Service 测试规范](#7-service-测试规范)
8. [Controller 接口测试规范](#8-controller-接口测试规范)
10. [测试数据与隔离](#10-测试数据与隔离)
11. [执行与 CI](#11-执行与-ci)
12. [覆盖率红线与推进节奏](#12-覆盖率红线与推进节奏)

---

## 1. 现状与目标

### 1.1 现状盘点（2026-08-30 实测，架构测试已落地后更新）

| 项 | 状态 |
|----|------|
| `src/test/java` 目录 | ✅ `ruoyi-admin`（架构）+ `ruoyi-system`（业务） |
| 用例总数 | ✅ **56 个，全绿**（架构 15 + 业务 41） |
| `spring-boot-starter-test` | ✅ 已接入父 POM（test scope） |
| ArchUnit | ✅ 已接入，`1.3.0` |
| H2（内存库） | ✅ 已接入父 POM（test scope），`MODE=MySQL` |
| `mybatis-spring-boot-starter-test` | ✅ 已接入 `ruoyi-system`，`2.3.1` |
| `maven-surefire-plugin` | ✅ 已锁定 `2.22.2`（默认 2.12.4 不识别 JUnit 5） |
| Controller 接口测试 | ❌ 仍为 0（P1-5） |
| CI 流水线 | ❌ 无 |

**已有测试类清单**

| 模块 | 测试类 | 用例 | 类型 |
|------|--------|------|------|
| `ruoyi-admin` | `architecture/ArchitectureRulesTest` | 11 + 1 自检 | 架构约束 |
| `ruoyi-admin` | `architecture/MapperXmlRulesTest` | 3 | XML 约束 |
| `ruoyi-system` | `biz/service/impl/SysProductServiceImplTest` | 10 | Mockito 单测 |
| `ruoyi-system` | `biz/service/impl/SysStudentServiceImplTest` | 8 | Mockito 单测 |
| `ruoyi-system` | `biz/mapper/SysProductMapperTest` | 11 | `@MybatisTest` + H2 |
| `ruoyi-system` | `biz/mapper/SysStudentMapperTest` | 12 | `@MybatisTest` + H2 |

```bash
mvn clean test                                         # 全量，实测 BUILD SUCCESS
mvn test -pl ruoyi-admin  -am -Dtest=ArchitectureRulesTest,MapperXmlRulesTest
mvn test -pl ruoyi-system -am -Dtest='Sys*Test'
```

**2 个 skipped 是用 `@Disabled` 固化的已知缺陷，不是失败**

| 用例 | 缺陷 | 验收方式 |
|------|------|---------|
| `SysProductServiceImplTest.deleteSysProductByProductIds_hasChildren_...` | P1-2：删除父节点未校验子节点，产生孤儿数据 | 修复后移除 `@Disabled`，用例应转为通过 |
| `SysProductMapperTest.selectSysProductList_byParentId_shouldFilter` | P1-2 前置：XML 的 `where` 不支持 `parent_id` 条件 | 同上 |

> 这是刻意设计：缺陷不该只写在文档里等待被遗忘，
> 而应固化成"当前会失败/跳过的测试"，修复时自然获得验收标准。

### 1.2 目标

| 阶段 | 目标 | 覆盖范围 |
|------|------|---------|
| 第一阶段 | 补 Service 层单测 | `com.ruoyi.biz.service.impl` 全部 |
| 第二阶段 | 补 Mapper 层 SQL 验证 | `mapper/biz/*.xml` 全部语句 |
| 第三阶段 | 补 Controller 契约测试 | `/biz/**` 全部接口 |
| 长期 | 新代码必须带测试 | 见 [第 12 节](#12-覆盖率红线与推进节奏) |

---

## 2. 测试金字塔与选型

```
        ╱╲          E2E / UI     —— 本项目不做（成本高，收益低）
       ╱  ╲
      ╱────╲        @SpringBootTest 接口测试  —— 覆盖 /biz/** 主流程
     ╱      ╲
    ╱────────╲      @MybatisTest Mapper 测试  —— 覆盖全部 SQL
   ╱          ╲
  ╱────────────╲    JUnit5 + Mockito 单测     —— 覆盖 Service 业务规则 ★重点
```

| 层级 | 框架 | 是否启动 Spring | 速度 | 优先级 |
|------|------|----------------|------|--------|
| Service 单测 | JUnit 5 + Mockito | 否（纯 Mock） | 毫秒级 | **P0** |
| Mapper 测试 | `@MybatisTest` + H2 | 是（切片） | 秒级 | **P0** |
| Controller 测试 | `@SpringBootTest` + MockMvc | 是（全量） | 十秒级 | P1 |

**原则**：能用纯 Mock 测的就不要起 Spring 容器。起容器的测试数量要严格控制。

---

## 3. 依赖接入（✅ 已完成）

### 3.1 父 POM 增加测试依赖

`spring-boot-starter-test` 的版本已由 `spring-boot-dependencies` BOM 仲裁（与 Boot `2.5.15` 对齐，
内含 **JUnit 5 (Jupiter 5.7.x)** + **Mockito 3.9.x** + AssertJ + Spring Test）。

在根 `pom.xml` 的 `<dependencyManagement>` **之后**新增 `<dependencies>`（注意：现有 POM 只有
`dependencyManagement`，没有全局 `dependencies`）：

```xml
<dependencies>
    <!-- 单元测试（由 spring-boot-dependencies BOM 仲裁版本） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Mapper 切片测试用的内存库 -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> 放到父 POM 后，6 个子模块自动继承，无需逐个添加。
> 若担心污染 `ruoyi-common`，也可只在 `ruoyi-system` 与 `ruoyi-admin` 的 POM 中分别声明。

### 3.2 必须一并锁定 Surefire 版本（踩坑记录）

本项目的父 POM **不是** `spring-boot-starter-parent`，因此 Maven 使用默认的
`maven-surefire-plugin` **2.12.4**。该版本**不识别 JUnit 5**，会导致：

> `mvn test` 构建成功，但**静默执行 0 个用例**——看起来全绿，实际什么都没测。

必须在父 POM 的 `<build><plugins>` 中显式锁定：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <!-- 必须 ≥ 2.22.0：Maven 默认的 2.12.4 不识别 JUnit 5，会静默跑 0 个用例 -->
    <version>2.22.2</version>
    <configuration>
        <argLine>-Dfile.encoding=UTF-8</argLine>
    </configuration>
</plugin>
```

### 3.3 验证

```bash
mvn clean test
# 期望：Tests run: 15, Failures: 0, Errors: 0  →  BUILD SUCCESS
```

> 若输出为 `Tests run: 0`，先检查 Surefire 版本是否为 2.12.4。

---

## 4. 目录与命名

### 4.1 目录结构（镜像 main）

**测试类放在被测代码所属的模块**，遵循 Maven 标准：

```
ruoyi-system/src/test/
├── java/com/ruoyi/biz/
│   ├── service/impl/SysProductServiceImplTest.java
│   ├── mapper/SysProductMapperTest.java
│   └── domain/SysProductTest.java
└── resources/
    ├── application-test.yml
    └── sql/
        ├── schema.sql        # 建表
        └── data.sql          # 测试数据

ruoyi-admin/src/test/
├── java/com/ruoyi/biz/controller/SysProductControllerTest.java
└── resources/application-test.yml
```

### 4.2 命名

| 类型 | 命名 | 示例 |
|------|------|------|
| 测试类 | `<被测类>Test` | `SysProductServiceImplTest` |
| 测试方法 | `方法名_场景_预期结果` | `insertProduct_nameDuplicate_throwServiceException` |
| 测试类 Javadoc | 说明被测对象 + 覆盖点 | 见下方模板 |

```java
/**
 * SysProductServiceImpl 单元测试
 *
 * 覆盖：新增 / 修改 / 删除 / 查询 的正常与异常分支
 *
 * @author life
 * @date 2026-08-30
 */
```

### 4.3 断言风格

统一用 **AssertJ**（`spring-boot-starter-test` 自带，链式可读性好）：

```java
assertThat(result).isNotNull();
assertThat(result.getProductName()).isEqualTo("模具管理系统");
assertThatThrownBy(() -> service.insertSysProduct(dup))
    .isInstanceOf(ServiceException.class)
    .hasMessageContaining("产品名称已存在");
```

---

## 5. 单元测试规范

### 5.1 模板（Service 层，纯 Mock，不起容器）

```java
package com.ruoyi.biz.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.mapper.SysProductMapper;
import com.ruoyi.common.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SysProductServiceImpl 单元测试
 *
 * @author life
 * @date 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class SysProductServiceImplTest
{
    @Mock
    private SysProductMapper sysProductMapper;

    @InjectMocks
    private SysProductServiceImpl sysProductService;

    @Test
    @DisplayName("根据主键查询 - 存在时返回产品")
    void selectSysProductByProductId_exist_returnsProduct()
    {
        // given
        SysProduct expected = new SysProduct();
        expected.setProductId(1L);
        expected.setProductName("模具管理系统");
        given(sysProductMapper.selectSysProductByProductId(1L)).willReturn(expected);

        // when
        SysProduct actual = sysProductService.selectSysProductByProductId(1L);

        // then
        assertThat(actual.getProductName()).isEqualTo("模具管理系统");
        verify(sysProductMapper).selectSysProductByProductId(1L);
    }

    @Test
    @DisplayName("批量删除 - 空数组时不调用 Mapper")
    void deleteSysProductByProductIds_emptyArray_skipsMapper()
    {
        // when
        sysProductService.deleteSysProductByProductIds(new Long[0]);

        // then
        verify(sysProductMapper, never()).deleteSysProductByProductIds(any());
    }
}
```

### 5.2 强制约定

| # | 约定 |
|---|------|
| 1 | 测试类与方法**不写 `public`**（JUnit 5 下冗余） |
| 2 | 方法名用 `下划线分隔的三段式`：`被测方法_场景_预期` |
| 3 | 方法体用 `// given` / `// when` / `// then` 三段注释分隔 |
| 4 | 每个测试方法只验证**一个**行为 |
| 5 | 只 Mock **直接依赖**（Mapper），不 Mock 被测类内部逻辑 |
| 6 | 异常分支必须覆盖（用 `assertThatThrownBy`） |
| 7 | 禁止用 `Thread.sleep` 等待异步 |
| 8 | 禁止测试间共享可变状态 |

### 5.3 工具类单测（纯函数，优先级最高）

`ruoyi-common` 下的 `StringUtils` / `DateUtils` / `SqlUtil` / `ExcelUtil` 等无依赖纯函数，
**零成本、收益最高，建议首批补齐**：

```java
class SqlUtilTest
{
    @Test
    @DisplayName("escapeOrderBySql - 过滤非法排序字段")
    void escapeOrderBySql_illegalColumn_returnsEmpty()
    {
        assertThat(SqlUtil.escapeOrderBySql("id; drop table sys_user")).isEmpty();
    }
}
```

---

## 6. Mapper 测试规范

### 6.1 用 `@MybatisTest` 跑切片测试

只加载 MyBatis + 数据源，不启动 Web 层，秒级完成。

```java
package com.ruoyi.biz.mapper;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.biz.domain.SysProduct;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysProductMapper 数据访问测试
 *
 * 使用 H2 内存库 + MySQL 兼容模式，验证所有 SQL 语句可正确执行
 *
 * @author life
 * @date 2026-08-30
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = { "/sql/schema.sql", "/sql/data.sql" })
@Transactional
class SysProductMapperTest
{
    @Autowired
    private SysProductMapper sysProductMapper;

    @Test
    @DisplayName("selectSysProductList - 按名称模糊查询")
    void selectSysProductList_byName_returnsMatched()
    {
        SysProduct query = new SysProduct();
        query.setProductName("模具");

        List<SysProduct> list = sysProductMapper.selectSysProductList(query);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getProductName()).contains("模具");
    }

    @Test
    @DisplayName("insertSysProduct - 主键回填")
    void insertSysProduct_valid_returnsGeneratedKey()
    {
        SysProduct product = new SysProduct();
        product.setProductName("新产品");
        product.setStatus("0");

        int rows = sysProductMapper.insertSysProduct(product);

        assertThat(rows).isEqualTo(1);
        assertThat(product.getProductId()).isNotNull();   // useGeneratedKeys 生效
    }
}
```

### 6.2 在本项目跑通 `@MybatisTest` 的两个前提（实测踩坑）

**前提一：模块内必须有一个 `@SpringBootConfiguration`**

`@MybatisTest` 会从测试类所在包（如 `com.ruoyi.biz.mapper`）**向上逐级查找**
`@SpringBootConfiguration`。真正的启动类 `RuoYiApplication` 在 `ruoyi-admin` 模块，
而 `ruoyi-system` 的测试 classpath 上**没有**它，于是报：

```
IllegalStateException: Unable to find a @SpringBootConfiguration
```

解决办法：在 `ruoyi-system/src/test/java/com/ruoyi/` 下放一个测试专用配置类
（见 `TestMybatisApplication`）。注意用 `@SpringBootConfiguration` + `@EnableAutoConfiguration`，
**不要**用 `@SpringBootApplication`——后者会触发 `@ComponentScan` 拉起整个应用，就不是切片测试了。

**前提二：需要显式 `@MapperScan`**

本项目的 Mapper 接口**没有** `@Mapper` 注解（RuoYi 靠 `ruoyi-framework` 里
`MyBatisConfig` 的 `@MapperScan` 扫描）。而 `ruoyi-system` 的测试不会加载 framework，
所以必须在测试配置类上补 `@MapperScan("com.ruoyi.**.mapper")`，否则注入 Mapper 时失败。

**另需依赖**：`ruoyi-system/pom.xml` 要显式加 `mybatis-spring-boot-starter-test`
（`2.3.1`，与 pagehelper 传递引入的版本对齐），因为该模块编译期本身没有 MyBatis。

### 6.3 测试数据源配置

`ruoyi-system/src/test/resources/application-test.yml`：

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:ruoyi_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password:
  sql:
    init:
      mode: never          # 由 @Sql 显式控制，避免与脚本冲突

mybatis:
  typeAliasesPackage: com.ruoyi.**.domain
  mapperLocations: classpath*:mapper/**/*Mapper.xml
  configLocation: classpath:mybatis/mybatis-config.xml
```

> H2 的 `MODE=MySQL` 能覆盖绝大多数语法。**分页 SQL 与 MySQL 方言函数若有差异，
> 该类用例需改用真实 MySQL（见下）**。

### 6.4 H2 不适用时

涉及 MySQL 方言（如 `find_in_set`、复杂分页）的语句，改用 **Testcontainers** 拉真实 MySQL：

```java
@Testcontainers
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SysProductMapperMysqlTest
{
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("ruoyi_test")
        .withUsername("root")
        .withPassword("test");
    // ...
}
```

> 需要额外引入 `org.testcontainers:mysql` 与 `junit-jupiter` 依赖，且本机需可用 Docker。
> **默认不启用**，仅在 H2 跑不通时针对个别用例开启。

---

## 7. Service 测试规范

Service 层是本项目的**测试主战场**（Mapper 之上、Controller 之下的业务规则都在这）。

### 7.1 两种写法的选择

| 场景 | 写法 |
|------|------|
| 逻辑集中在 Service，只依赖 Mapper | **纯 Mockito**（第 5 节模板），快 |
| 需要事务、数据权限、多表联动 | `@SpringBootTest` + `@Transactional` 回滚 |

### 7.2 需要 Spring 上下文时的模板

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional          // 每个用例结束自动回滚
class SysProductServiceIntegrationTest
{
    @Autowired
    private ISysProductService sysProductService;

    @Test
    @DisplayName("insertSysProduct - 名称重复时抛 ServiceException")
    void insertSysProduct_duplicateName_throwsServiceException()
    {
        SysProduct first = new SysProduct();
        first.setProductName("重复名称");
        sysProductService.insertSysProduct(first);

        SysProduct second = new SysProduct();
        second.setProductName("重复名称");

        assertThatThrownBy(() -> sysProductService.insertSysProduct(second))
            .isInstanceOf(ServiceException.class);
    }
}
```

### 7.3 必测清单（每个 Service）

- [ ] 查询：正常返回 / 空结果 / 条件命中
- [ ] 新增：成功 / 必填缺失 / 唯一约束冲突
- [ ] 修改：成功 / 主键不存在（影响行数 0）
- [ ] 删除：单个 / 批量 / 空数组 / 不存在的主键
- [ ] 树形实体额外：**删除父节点时子节点的处理**（当前实现未校验，属已知缺陷）
- [ ] 所有 `throw new ServiceException(...)` 分支

---

## 8. Controller 接口测试规范

### 8.1 目标

验证三件事：**URL 可达**、**权限注解生效**、**响应结构正确**。不追求全覆盖，只覆盖主流程与鉴权。

### 8.2 模板

```java
package com.ruoyi.biz.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysProductController 接口测试
 *
 * @author life
 * @date 2026-08-30
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SysProductControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /biz/product/list - 未登录返回 401")
    void list_unauthenticated_returns401() throws Exception
    {
        mockMvc.perform(get("/biz/product/list"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /biz/product/list - 已登录返回 200 与标准结构")
    void list_authenticated_returnsStandardBody() throws Exception
    {
        mockMvc.perform(get("/biz/product/list")
                .header("Authorization", getAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * 获取管理员 Token（复用登录接口）
     */
    private String getAdminToken() throws Exception
    {
        // 调用 /login 获取 token，或直接从 Redis 写入一个测试用 LoginUser
        return "Bearer " + testToken;
    }
}
```

### 8.3 鉴权测试的处理方式

RuoYi 的鉴权依赖 Redis 中的 `LoginUser`。两种方案：

| 方案 | 做法 | 适用 |
|------|------|------|
| A. 走登录接口 | 用例里先调 `/login` 拿 token | 真实但慢，且依赖验证码开关 |
| B. 直接写 Redis | 用 `@Autowired TokenService` 造一个 `LoginUser` 并 `setUserAgent` | **推荐**，快且可控 |

方案 B 示例：

```java
@Autowired
private TokenService tokenService;

private String mockLogin(String username)
{
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(1L);
    loginUser.setUsername(username);
    // 注入 admin 全权限
    loginUser.setPermissions(Set.of("*:*:*"));
    return "Bearer " + tokenService.createToken(loginUser);
}
```

### 8.4 断言要点

- 状态码：`status().isOk()` / `isUnauthorized()` / `isForbidden()`
- 业务码：`jsonPath("$.code").value(200)`
- 分页结构：`jsonPath("$.rows").isArray()` + `jsonPath("$.total").isNumber()`
- 错误信息：`jsonPath("$.msg").value(...)`

---

## 10. 测试数据与隔离

### 10.1 三不原则

1. **不依赖**开发库 / 测试环境的真实数据
2. **不污染**：每个用例结束必须回滚（`@Transactional`）
3. **不共享**：用例之间不依赖执行顺序

### 10.2 构造数据的方式

| 方式 | 适用 | 备注 |
|------|------|------|
| 对象 + setter | Service / Controller 层 | 简单直接（本项目无 Lombok，需手写） |
| `@Sql` 脚本 | Mapper 层 | 放 `src/test/resources/sql/` |
| Builder 方法 | 实体字段多时 | 在测试类内写私有 `buildProduct(...)` |

```java
private SysProduct buildProduct(String name, String status)
{
    SysProduct product = new SysProduct();
    product.setProductName(name);
    product.setStatus(status);
    product.setParentId(0L);
    product.setOrderNum(1);
    return product;
}
```

### 10.3 需要 Redis 的用例

`TokenService` / 缓存相关用例需要 Redis。两种处理：

- 本机起 Redis（最简单，与开发环境共用 localhost:6379，用 `database: 5` 隔离）
- 或引入 embedded-redis（额外依赖，暂不推荐）

`src/test/resources/application-test.yml`：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 5      # 独立于开发库，避免互相污染
```

---

## 11. 执行与 CI

### 11.1 本地执行

```bash
# 跑全部测试
mvn test

# 只跑某个模块
mvn test -pl ruoyi-system

# 只跑某个类
mvn test -pl ruoyi-system -Dtest=SysProductServiceImplTest

# 只跑某个方法
mvn test -pl ruoyi-system -Dtest=SysProductServiceImplTest#insertProduct_duplicateName_throw

# 跳过测试（紧急打包时用，不推荐常态化）
mvn clean package -DskipTests
```

### 11.2 测试 Profile

所有需要 Spring 上下文的测试类统一加 `@ActiveProfiles("test")`，
配置落在各模块 `src/test/resources/application-test.yml`。

### 11.3 CI 建议

在 `.github/workflows/` 下新增 `ci.yml`：

```yaml
name: CI
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '8', distribution: 'temurin' }
      - name: Build & Test
        run: mvn -B clean test
      - name: Package
        run: mvn -B package -DskipTests
```

> 需要 Redis 的用例在 CI 上需额外起 service 容器（Redis 官方 action 即可）。

---

## 12. 覆盖率红线与推进节奏

### 12.1 红线

| 层级 | 目标行覆盖率 | 说明 |
|------|------------|------|
| `com.ruoyi.biz.service.impl` | **≥ 70%** | 业务核心，强制 |
| `com.ruoyi.biz.mapper` | **≥ 60%** | 至少保证每条 SQL 可执行 |
| `com.ruoyi.biz.controller` | 不设覆盖率，只要求主流程 + 鉴权用例 | |
| `com.ruoyi.common.utils` | **≥ 50%** | 纯函数，性价比最高 |
| `ruoyi-framework` / `ruoyi-generator` | 不设要求 | 框架代码，改动少 |

### 12.2 新代码规则（立即生效）

> **新增或修改 `com.ruoyi.biz` 下的任何类，必须同步提供对应测试类。**
> 无测试的 PR 不予合入。

### 12.3 存量补齐顺序

| 批次 | 内容 | 理由 |
|------|------|------|
| 第 1 批 | `common/utils` 纯函数 | 零依赖、零成本、快速建立信心 |
| 第 2 批 | `SysProductServiceImpl` / `SysStudentServiceImpl` | 业务主战场 |
| 第 3 批 | `SysProductMapper` / `SysStudentMapper` 全部 SQL | 暴露 SQL 错误与 resultMap 遗漏 |
| 第 4 批 | `/biz/**` Controller 主流程 + 鉴权 | 防越权回归 |

### 12.4 提交前自检

- [ ] 新代码有对应测试类
- [ ] 方法名是 `被测方法_场景_预期` 三段式
- [ ] 有 `// given` / `// when` / `// then` 分段
- [ ] 正常 + 异常分支都覆盖
- [ ] 用例之间无顺序依赖
- [ ] 需要 Spring 的用例加了 `@ActiveProfiles("test")` 与 `@Transactional`
- [ ] `mvn test` 全绿

---

## 附：与现有代码的冲突提示

| 事项 | 说明 |
|------|------|
| 项目 Java 是 **1.8** | JUnit 5 支持 Java 8，可用；但不可用 `var`、文本块等新语法 |
| Spring Boot **2.5.15** | 自带 JUnit Jupiter 5.7.x，无需额外指定版本 |
| 未引入 Lombok | 造数据必须手写 setter，建议用私有 builder 方法减少重复 |
| `mapUnderscoreToCamelCase` 已关闭 | Mapper 测试能直接暴露 `resultMap` 遗漏，**价值极高** |
| `src/test` 已建立 | `ruoyi-admin` / `ruoyi-system` 下均需在 IDE 里标记为 Test Sources Root；新增测试按模块放入即可 |
