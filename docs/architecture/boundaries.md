# 模块边界与依赖规则

> 权威来源：各模块 `pom.xml` 的实际 `<dependency>` 声明
> 相关文档：[系统架构总览](./overview.md) ｜ [编码规范](../conventions/README.md)

---

## 1. 依赖图（当前事实）

```
                        ruoyi-admin  （启动模块，唯一可执行的 jar）
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
  ruoyi-framework   ruoyi-quartz   ruoyi-generator
        │               │               │
        ▼               │               │
   ruoyi-system         │               │
        │               │               │
        ▼               ▼               ▼
        └─────────►  ruoyi-common  ◄────┘
                          │
                       （无 ruoyi 内部依赖）
```

### 1.1 依赖声明清单

| 模块 | 直接依赖的 ruoyi 模块 |
|------|---------------------|
| `ruoyi-admin` | `ruoyi-framework`、`ruoyi-quartz`、`ruoyi-generator` |
| `ruoyi-framework` | `ruoyi-system` |
| `ruoyi-system` | `ruoyi-common` |
| `ruoyi-quartz` | `ruoyi-common` |
| `ruoyi-generator` | `ruoyi-common` |
| `ruoyi-common` | **无**（只允许第三方依赖） |

### 1.2 传递依赖带来的可用性

`ruoyi-admin` 通过传递依赖可以使用 `ruoyi-system`、`ruoyi-common` 的全部公开类。
**但要注意**：可用的 ≠ 该直接 import 的。见下方规则。

---

## 2. 铁律

### 规则 1：`ruoyi-common` 保持无状态、无业务

**允许**：工具类、常量、枚举、注解、异常、通用基类、通用过滤器。

**禁止**：

- ❌ 依赖任何 `ruoyi-*` 模块（当前为 0，必须保持）
- ❌ 引用任何业务表、业务字段、业务枚举（如 `biz:product:*` 权限串）
- ❌ 注入 Spring Bean（除极少数 `utils/spring/SpringUtils` 这类上下文工具类）
- ❌ 出现 `sys_product` / `sys_student` 之类的具体业务表名

### 规则 2：依赖只能自上而下，禁止反向与同层环

- ❌ `common` → 任何模块
- ❌ `system` → `framework` / `admin`
- ❌ `framework` → `admin`
- ❌ `quartz` ↔ `generator`（同层互不依赖）
- ❌ 任何形式的循环依赖

### 规则 3：Controller 只放在 `ruoyi-admin`

所有 `@RestController` / `@Controller` 必须在 `ruoyi-admin` 下：

| 允许的 Controller 位置 | 用途 |
|----------------------|------|
| `ruoyi-admin/.../com.ruoyi.web.controller.system` | 系统管理 |
| `ruoyi-admin/.../com.ruoyi.web.controller.monitor` | 系统监控 |
| `ruoyi-admin/.../com.ruoyi.web.controller.common` | 通用（验证码、上传下载） |
| `ruoyi-admin/.../com.ruoyi.web.controller.tool` | 工具类接口 |
| `ruoyi-admin/.../com.ruoyi.biz.controller` | **自有业务** |
| `ruoyi-quartz/.../com.ruoyi.quartz.controller` | 定时任务（模块自带，历史遗留） |

> `ruoyi-system`、`ruoyi-framework`、`ruoyi-common` 中**不得**出现 Controller。

### 规则 4：SQL 只能写在 Mapper XML

- ❌ 禁止在 Service / Controller 里拼 SQL 字符串
- ❌ 禁止在 Java 代码里写 `select` / `insert` 等关键字拼接
- ✅ 动态条件用 MyBatis `<if>` / `<where>` / `<foreach>` / `<trim>`
- ✅ 排序字段必须过 `SqlUtil.escapeOrderBySql()`（防注入）

### 规则 5：跨模块访问必须走接口，不走 Mapper

`ruoyi-admin` 需要数据时：

```
Controller → I Xxx Service（接口）→ XxxServiceImpl → XxxMapper
```

- ❌ Controller 直接 `@Autowired` Mapper
- ❌ 跨业务模块之间直接注入对方的 Mapper

## 3. `com.ruoyi.biz` 业务包边界（本项目特有）

业务代码**按层跨模块拆分**，这是本项目最重要的边界约定：

```
ruoyi-admin  ──►  com.ruoyi.biz.controller         （表现层，只做参数与响应）
                        │  依赖
                        ▼
ruoyi-system ──►  com.ruoyi.biz.service.IXxxService  （接口）
                  com.ruoyi.biz.service.impl         （实现）
                  com.ruoyi.biz.mapper               （数据访问接口）
                  com.ruoyi.biz.domain               （实体）
                  resources/mapper/biz/*.xml         （SQL）
                        │  依赖
                        ▼
ruoyi-common ──►  BaseEntity / TreeEntity / AjaxResult / BaseController …
```

### 3.1 为什么这么分

`ruoyi-admin` 是启动与装配模块，只放 Controller；`ruoyi-system` 承载领域模型与持久化。
好处是业务实体与 SQL 集中在同一个模块，便于以后把 `biz` 整体抽成独立 Maven 模块。

### 3.2 迁移到独立模块的前置条件

若后续要把 `com.ruoyi.biz` 抽成 `ruoyi-biz` 独立模块，需满足：

1. `biz` 包内不反向 import `ruoyi-admin` 的任何类
2. `biz` 包内不 import `ruoyi-quartz` / `ruoyi-generator`
3. `biz` 只依赖 `ruoyi-common`（当前满足）

**当前状态：已满足**，可平滑迁移。

---

## 4. 边界检查清单（Code Review 用）

提交前逐条自检：

- [ ] 新增的 Controller 在 `ruoyi-admin` 下吗？
- [ ] 新增的实体在 `**.domain` 包下吗？（否则 MyBatis 别名扫描不到）
- [ ] 新增的 Mapper XML 以 `Mapper.xml` 结尾且在 `resources/mapper/**` 下吗？
- [ ] `ruoyi-common` 有没有被塞进业务逻辑？
- [ ] 有没有出现反向依赖（低层 import 高层）？
- [ ] Controller 有没有直接注入 Mapper？
- [ ] SQL 有没有写在 Java 代码里？
- [ ] 排序字段有没有过 `SqlUtil.escapeOrderBySql()`？

---

## 5. 自动化校验（已落地）

本节的规则**不是建议，而是可执行断言**。违反时 `mvn test` 直接失败。

### 5.0 ArchUnit 结构化测试

| 项 | 值 |
|----|-----|
| 测试类 | `ruoyi-admin/src/test/java/com/ruoyi/architecture/ArchitectureRulesTest.java` |
| 补充测试 | `ruoyi-admin/src/test/java/com/ruoyi/architecture/MapperXmlRulesTest.java` |
| 机制 | ArchUnit `1.3.0` + JUnit 5 |
| 声明文件 | `.harness/enforcement.yml` |
| 用例数 | 15（11 条架构规则 + 3 条 XML 规则 + 1 条自检） |

```bash
# 只跑架构约束（快，约 8 秒）
mvn test -pl ruoyi-admin -am -Dtest=ArchitectureRulesTest,MapperXmlRulesTest

# 全量测试
mvn clean test
```

**已编码的规则**

| # | 规则 | 作用域 | 严重度 |
|---|------|--------|--------|
| 1 | `ruoyi-common` 不得依赖任何 ruoyi 模块 | `com.ruoyi.common..` | high |
| 2 | Controller 只允许在 admin / quartz / generator | 全仓库 | high |
| 3 | **业务 Controller 公开方法必须有 `@PreAuthorize`** | `com.ruoyi.biz.controller..` | **critical** |
| 4 | Controller 不得直接依赖 Mapper | 全仓库 | high |
| 5 | 业务 Service 接口必须以 `I` 开头 | `com.ruoyi.biz.service..` | medium |
| 6 | 业务 Service 实现必须以 `ServiceImpl` 结尾 | `com.ruoyi.biz.service.impl..` | medium |
| 7 | Mapper 接口必须在 `..mapper..` 包下 | 全仓库 | high |
| 8 | 业务 Service 实现必须在 `..service.impl..` | `com.ruoyi.biz..` | medium |
| 9 | 业务实体必须继承 `BaseEntity` / `TreeEntity` | `com.ruoyi.biz.domain..` | medium |
| 10 | 禁止引入 Lombok | 全仓库 | high |
| 11 | 顶层包之间不得成环 | `com.ruoyi.(*)..` | high |
| 12 | Mapper XML 必须以 `Mapper.xml` 结尾 | `mapper/**` | high |
| 13 | 每个 Mapper 接口必须有对应 XML | 全仓库 | high |
| 14 | XML 的 namespace 必须可解析为类 | `mapper/**` | high |
| 15 | 自检：确认各模块类都在 classpath 上 | — | — |

**为什么规则 8 只约束 `com.ruoyi.biz`**

实测发现 `ruoyi-framework` 的 `UserDetailsServiceImpl`、`ruoyi-generator` 的
`GenTableServiceImpl` 位于各自的 `service` 包（非 `.impl`），属 RuoYi 原生结构。
我们**不改造框架源码**，因此把规则作用域收窄到自有业务包，避免产生无法修复的告警。

> 经验：一条会在"我们不打算改的代码"上失败的规则，是不可执行的噪音，应当收窄而非忽略。

**错误信息设计**

所有规则都带 `because(...)` 说明，格式为「是什么问题 / 为什么 / 怎么修 / 去哪看」，
便于 AI Agent 读到报错后自主修复。实测输出示例：

```
Rule 'methods that are declared in classes that reside in a package
'com.ruoyi.biz.controller..' and are public should be annotated with
@PreAuthorize, because 安全红线：... 怎么修：补 @PreAuthorize，权限串需与
sys_menu.perms 及按钮权限声明完全一致。详见 docs/architecture/boundaries.md'
was violated (1 times):
Method <com.ruoyi.biz.controller.TempUnsecuredController.leak()>
is not annotated with @PreAuthorize in (TempUnsecuredController.java:21)
```

> ⚠️ 第 15 条自检很重要：如果 classpath 缺少某模块的类，ArchUnit 规则会**空跑通过**，
> 给出虚假的安全感。自检用例专门防止这种情况。

### 5.1 Maven 依赖分析

```bash
# 查看依赖树，确认没有非法依赖
mvn dependency:tree -Dincludes=com.ruoyi

# 检测循环依赖与未使用声明
mvn dependency:analyze
```

### 5.2 期望输出

```
com.ruoyi:ruoyi-admin:jar:3.9.2
+- com.ruoyi:ruoyi-framework:jar:3.9.2:compile
|  \- com.ruoyi:ruoyi-system:jar:3.9.2:compile
|     \- com.ruoyi:ruoyi-common:jar:3.9.2:compile
+- com.ruoyi:ruoyi-quartz:jar:3.9.2:compile
|  \- com.ruoyi:ruoyi-common:jar:3.9.2:compile
\- com.ruoyi:ruoyi-generator:jar:3.9.2:compile
   \- com.ruoyi:ruoyi-common:jar:3.9.2:compile
```

若输出中出现 `ruoyi-common` 下面挂着任何 `ruoyi-*` 模块，即视为**边界破坏**，需立即修正。

---

## 6. 常见越界场景与正确做法

| 场景 | ❌ 错误做法 | ✅ 正确做法 |
|------|-----------|-----------|
| 想在 `common` 里查业务表 | 在 `common` 注入 `XxxMapper` | 逻辑放回 `system`，`common` 只留无状态工具 |
| Controller 想拿单个字段 | 直接注入 `XxxMapper` 查询 | 在 `IXxxService` 加方法，Controller 调 Service |
| 定时任务要调业务接口 | quartz 模块里 import `ruoyi-system` 的包 | 新建任务类放 `ruoyi-quartz/.../task`，通过接口/Rest 调用，或把共用逻辑下沉到 `common` |
| 页面想复用 system 页面 | 直接改 `views/system/**` | 在 `views/biz/` 下新建页面，复制后按业务改造 |
| 需要跨模块常量 | 各模块各写一份字面量 | 放到 `common/constant`，或抽成枚举放 `common/enums` |
| 想绕过权限直接查数据 | Service 里写裸 SQL | 走 `@DataScope` 数据权限注解 |
