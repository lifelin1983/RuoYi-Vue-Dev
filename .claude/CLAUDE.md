# RuoYi-Vue — Agent 指南

RuoYi-Vue `3.9.2` 后台管理系统。后端 Spring Boot 多模块 + MyBatis + Spring Security(JWT)，
前端 Vue2 SPA。自有业务包 `com.ruoyi.biz`（产品管理、学生管理）。
后端 `:8080`，前端 dev `:80`（代理转发）。MySQL(Druid) + Redis。

## 快速导航

| 你想做什么 | 去哪里看 |
|-----------|---------|
| 了解系统架构 | docs/architecture/overview.md |
| 了解模块边界和依赖规则 | docs/architecture/boundaries.md |
| 了解编码规范 | docs/conventions/README.md |
| 了解当前迭代任务 | docs/plans/current-sprint.md |
| 了解 API 规范 | docs/reference/api-spec.yaml |
| 了解错误码 | docs/reference/error-codes.md |
| 了解测试规范 | docs/conventions/testing.md |

## 五条铁律

> 每条都对应真实故障或安全漏洞，不是通用建议。细则见 docs/architecture/boundaries.md。

1. **实体加字段必须改三处**：实体类 / `resultMap` / `selectXxxVo`。
   `mapUnderscoreToCamelCase` 已关闭，漏一处该字段永远查出 null。
2. **Mapper XML 必须以 `Mapper.xml` 结尾**且在 `resources/mapper/**` 下。
   否则 `mapperLocations` 扫不到，运行时报 `BindingException`。
3. **Controller 每个方法必须带 `@PreAuthorize("@ss.hasPermi('...')")`**。
   漏了就是真实越权漏洞——前端隐藏按钮不算防护。
4. **`ruoyi-common` 不得依赖任何 ruoyi 模块、不得写业务逻辑**。
   它只放无状态工具、常量、注解、异常、基类。
5. **业务代码按层跨模块落地**（见下表）。Controller 放进 `ruoyi-system` 会破坏依赖方向。

## 新代码落位（`com.ruoyi.biz`）

| 层 | 模块 | 路径 |
|----|------|------|
| Controller | `ruoyi-admin` | `com/ruoyi/biz/controller/` |
| domain | `ruoyi-system` | `com/ruoyi/biz/domain/` |
| mapper 接口 | `ruoyi-system` | `com/ruoyi/biz/mapper/` |
| mapper XML | `ruoyi-system` | `resources/mapper/biz/` |
| service 接口 | `ruoyi-system` | `com/ruoyi/biz/service/` |
| service 实现 | `ruoyi-system` | `com/ruoyi/biz/service/impl/` |
| 前端 API | `ruoyi-ui` | `src/api/biz/` |
| 前端页面 | `ruoyi-ui` | `src/views/biz/` |

命名约定：实体 `Xxx`、Service 接口 `IXxxService`、实现 `XxxServiceImpl`。
标准写法照抄 `SysProductController` / `SysStudentController`。

## 技术栈

Java 8 · Spring Boot `2.5.15` · MyBatis · Druid · Redis · JJWT · Spring Security `5.7.14`
Vue `2.6.12` · Element UI `2.15.14` · vue-cli 4 · MySQL

> 前端是 **Vue2 Options API**，禁止 `<script setup>` 与 Vue3 语法。
> 后端**未引入 Lombok**，getter/setter 全部手写。

## 验证命令

```bash
mvn clean verify -DfailIfNoTests=false               # 全量测试 + JaCoCo 覆盖率门禁（须全绿）
# 下面两条带 -Dtest 的命令必须加 -DfailIfNoTests=false：
# -am 会把 ruoyi-common 等模块拉进反应堆，而 -Dtest 对所有模块生效，
# 无匹配用例的模块会报 "No tests were executed!" 直接失败（实测踩过）。
mvn test -pl ruoyi-admin -am -Dtest=ArchitectureRulesTest,MapperXmlRulesTest -DfailIfNoTests=false
mvn test -pl ruoyi-admin -am -Dtest=SysProductControllerTest -DfailIfNoTests=false   # Controller 接口测试 (P1-5)
mvn test -pl ruoyi-system -am -Dtest='Sys*Test' -DfailIfNoTests=false
mvn clean package -DskipTests -pl ruoyi-admin -am    # 后端打包
cd ruoyi-ui && npm run dev                           # 前端 :80
# 一次性启用本机 pre-commit 钩子（提交即拦文档漂移）：bash scripts/setup-hooks.sh
```

架构约束由 ArchUnit 强制执行（模块边界、权限注解、命名、Mapper XML、**依赖方向**），
违反即 `mvn test` 失败。规则清单见 docs/architecture/boundaries.md 第 5 节。

覆盖率由 JaCoCo 在 `verify` 阶段强制执行（门禁只覆盖 `com.ruoyi.biz` 业务实现层与实体层，
棘轮基线 service.impl 100% / domain ~50% / 整包 ~70%；`com.ruoyi.biz.controller` 属 Web 胶水层，
不纳入门禁，由 SysProductControllerTest 等接口测试保证行为正确）。新增或修改
`com.ruoyi.biz` 下的类必须同步补测试，否则 `mvn clean verify` 会因
`jacoco:check` 规则 violated 而 BUILD FAILURE。规则见 `.harness/enforcement.yml`
的 `coverage-gate`。

本机 pre-commit 钩子（`scripts/git-hooks/pre-commit`，由 `scripts/setup-hooks.sh` 一次性启用）
提交前先跑 `scripts/check-doc-links.sh --strict`，文档漂移即阻止提交；
架构约束与覆盖率门禁仍在 CI 强制。`SKIP_DOC_CHECK=1 git commit` 可临时跳过文档校验。

> Windows 下若 `mvn` 报 `找不到主类 org.codehaus.plexus.classworlds.launcher.Launcher`，
> 是 Git Bash 的路径转换问题，改用 `mvn.cmd` 即可。

## 可用命令

- `ry.bat` / `ry.sh` — 一键打包
- `mvn`、`npm` 常规命令

## 禁止操作

- 不改 `views/system/**`、`api/system/**`、`common/**` 等框架自带目录
- 不在 Java 代码里拼 SQL；排序字段必须过 `SqlUtil.escapeOrderBySql()`
- 不直连生产库执行写操作
- 不提交密钥：`token.secret` 与 Redis 密码当前是默认值，部署前必须替换
- 不引入 Lombok、不升级 Vue3（与全仓库风格冲突）

## 已知欠账

- `sys_product` / `sys_student` 建表 SQL 未纳入 `sql/`，新环境初始化会失败
- 树形删除不校验子节点，会产生孤儿数据（已用 2 个 `@Disabled` 用例固化缺陷，修复即验收）
- Controller 接口测试与前端测试尚未覆盖（P1-5 / P2-4）
- CI 门禁已接入（`.github/workflows/ci.yml`，含 JaCoCo 覆盖率门禁）；仅 pre-commit Hook 与 `dependency-direction` 约束扫描未落地（增强项，非阻塞）

改动高风险区域（SecurityConfig、JWT 过滤器、生成器模板等）前，
先查 docs/architecture/overview.md 第 8 节的风险清单。
