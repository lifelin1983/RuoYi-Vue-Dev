# 项目文件汇总（RuoYi-Vue · 从脚手架到 Harness Engineering）

> 范围说明：本汇总覆盖**源码、配置、AI 治理层、文档、数据**文件。
> **已排除**：`ruoyi-ui/node_modules`(296M)、各模块 `target/`(构建产物)、`.idea`（IDE 配置）等生成物。
> `ruoyi-ui` 含近 3 万文件（多为框架源码与依赖），按**目录/模块层级**描述，不逐文件罗列。
> 标注：`[脚手架]` = RuoYi-Vue 3.9.2 原自带；`[Harness]` = 围绕"驾驭工程"新增/演进。

---

## 一、AI / Harness 治理层（本项目核心增量）

### `.claude/CLAUDE.md`　[Harness]
- **功能**：给 AI Agent 的"宪法"与导航入口。含五条铁律、新代码落位表、技术栈、验证命令、覆盖率说明、禁止操作、已知欠账。
- **原理**：文档是 Agent 唯一真相源；把"必须怎么做"写成 Agent 启动时必读的规则，而非靠人提醒。
- **逻辑**：快速导航表把"想做什么→看哪个文档"映射好；五条铁律每条对应一次真实故障/漏洞（如实体加字段改三处、Controller 必带 @PreAuthorize）。
- **使用**：Agent 会话开始时自动读取；人类改动架构/规范后同步更新此处。

### `.harness/enforcement.yml`　[Harness]
- **功能**：机器可读的架构约束声明（Pillar 2 Architectural Constraints）。含模块分层、banned_imports、naming、security 红线、persistence、test_requirements（覆盖率门槛）、ci_jobs。
- **原理**：Agent failures are harness failures——违规先补此处约束，不调提示词。自然语言说明在 `docs/`，本文件只放结构化声明供 CI 与 Agent 消费。
- **逻辑**：`modules` 定义 6 层依赖方向（common0→system/quartz/generator1→framework2→admin3，禁反向/成环）；`banned_imports` 含 `no-lombok`/`controller-no-mapper`/`common-no-internal-deps`；`security` 强制 @PreAuthorize；`coverage-gate` 阈值与 `pom.xml` 严格一致；`measured_baseline` 由 nightly 回写。
- **使用**：ArchUnit 读 `test_classes` 跑架构测试；CI 读 `ci_jobs` 编排闸门；Agent 写代码前读 `modules`/`security` 对齐。

### `.harness/harness-debt.yaml`　[Harness]
- **功能**：熵/债务台账（DEBT-xxx），本仓库债务数据**唯一真相源**。
- **原理**：原 enforcement.yml 的 `still_missing`/`resolved_defects` 已迁至此，消除双源漂移。nightly 自动巡检 verifiable 项，假绿会被记 `drift`。
- **逻辑**：每条含 `id/kind/title/priority/status/verifiable/check`。`check.type`：`test_exists`(验证型，证据失效记 drift) / `coverage_delta`(监控型，跌破红线记 drift) / `new_untested_class`(监控型，新增未测类记 drift)。当前 DEBT-001~006（L0 验证型 + L1 监控型均已落地）。
- **使用**：`audit-harness-debt.py --rewrite` 实跑巡检并回写；PR 护栏生成草稿贴评论；人类采纳草稿时改名 `DEBT-0xx` 并入。

### `scripts/audit-harness-debt.py`　[Harness]
- **功能**：债务台账自动巡检（零依赖，仅标准库，GitHub Actions 直接 `python3` 可跑）。
- **原理**：把"债务是否还成立"从人工记忆变成可重复机器校验。
- **逻辑**：轻量行解析 ledger → 对 `verifiable:true` 项实跑 check（读 `src/test` 源码 + 聚合 `jacoco.csv`）→ 比对声明 status → 验证型失效或监控型 FAIL 记 `drift`（写 `drift_note`），恢复则自动撤销。无 `jacoco.csv` 时 `coverage_delta` 降级 SKIP 不误报。
- **使用**：`python3 scripts/audit-harness-debt.py`（仅报告）/ `--rewrite`（漂移则回写并 exit 2）；由 `ci.yml` 的 `nightly-audit-debt` job 调用。

### `scripts/pr-debt-guard.py`　[Harness]
- **功能**：PR 债务护栏（Level 2 自动巡检，零依赖）。
- **原理**：在 PR 路径提前暴露熵增（覆盖率退化 / 新增未测类），而非等 nightly。
- **逻辑**：复用 nightly 回写的 `measured_baseline` 作 diff 基准；对比 PR head 的 domain 整包 LINE% 与 service.impl 新增类；命中生成 PR 评论 Markdown（含可一键采纳的 DEBT 草稿 YAML）。退化容差 0.5pp 防浮点误报。exit 0 非阻断（门禁仍由 Gate 2 兜底）；不自动写台账（fork PR 写权/供应链风险）。
- **使用**：`python3 scripts/pr-debt-guard.py --pr <号> --jacoco-dir downloaded --out coverage-debt-guard.md`；由 `ci.yml` 的 `pr-debt-guard` job 调用。

### `scripts/sync-coverage-baseline.py`　[Harness]
- **功能**：Nightly 回写覆盖率基线（观测事实自动同步）。
- **原理**：人工维护的基线声明必然滞后 → 让机器从 `jacoco.csv` 实算并回写，根治漂移。
- **逻辑**：精确镜像 `pom.xml` jacoco includes 口径（service.impl 整包 / domain 整包+逐类 / biz bundle / common.utils 精确集合）；算出四个门禁目标真实值，改写 `enforcement.yml` 的 `measured_baseline` 段。策略归人（门槛人工维护）、观测归机器（实测自动同步）。
- **使用**：`python3 scripts/sync-coverage-baseline.py`；由 `ci.yml` 的 `nightly-sync-baseline` job 调用，值变则提交 `[skip ci]`。

### `scripts/check-doc-links.sh`　[Harness]
- **功能**：文档新鲜度闸门（Gate 1 / Pillar 3 熵管理·垃圾回收），最便宜最高收益。
- **原理**：文档漂移 = Agent 照着错的干；把"文档不能漂"从愿望变成确定性检查。
- **逻辑**：6 项检查——① CLAUDE.md 引用路径存在 ② enforcement.yml 声明路径存在 ③ docs/ 相对链接可解析（文件相对+根相对双解析，避免误报）④ 声明测试数 vs 源码 `@Test`+`@ArchTest` 实际数（两者都统计，否则漏算 11 个 ArchUnit 规则）⑤ pending_defects 引用方法仍存在 ⑥ 文档仍声称无用例、但代码已有用例的自相矛盾（stale-claim）。
- **使用**：`bash scripts/check-doc-links.sh`（错误 fail）/ `--strict`（警告也 fail）；CI Gate 1 与 pre-commit 钩子均调用。

### `scripts/setup-hooks.sh`　[Harness]
- **功能**：一次性启用本仓库 pre-commit 钩子。
- **原理**：把最便宜的文档闸门从"CI 才拦"提前到"提交即拦"。
- **逻辑**：`git config core.hooksPath scripts/git-hooks`，使 Git 提交时自动跑 `scripts/git-hooks/pre-commit`。
- **使用**：`bash scripts/setup-hooks.sh`（一次性）；跳过：`SKIP_DOC_CHECK=1 git commit` 或 `git commit --no-verify`。

### `scripts/git-hooks/pre-commit`　[Harness]
- **功能**：提交前文档新鲜度校验（本地护栏）。
- **原理**：保持本地提交秒级、离线、不依赖 Maven/网络；架构与覆盖率门禁仍在 CI 强制。
- **逻辑**：设 `SKIP_DOC_CHECK` 则跳过；否则跑 `check-doc-links.sh --strict`，失败 exit 1 阻止提交。
- **使用**：由 Git 在 `git commit` 时自动触发（需先 `setup-hooks.sh` 启用）。

### `.github/workflows/ci.yml`　[Harness]
- **功能**：CI/CD 闸门编排（不可合入的闸）。
- **原理**：把本地 `mvn test` 升级为机器强制的合入阻断。
- **逻辑**：5 个 job——`docs-freshness`(Gate1) / `build-and-test`(Gate2a 架构+Gate2b 全测+覆盖率，需 Redis service，聚合 jacoco 徽章+PR 评论) / `frontend-test`(jest 硬门禁，已入 required_status_checks) / `nightly-sync-baseline`(每日02:00回写基线) / `nightly-audit-debt`+`pr-debt-guard`(熵管理)。`schedule` 每天跑兜底，`concurrency` 取消旧运行。
- **使用**：push/PR 自动触发；分支保护 contexts=[Gate2, Gate1, Frontend] 使 PR 合入须三闸全绿。

### `Harness-Engineering-合规评估报告.html`　[Harness]
- **功能**：周期合规评估报告交付物（评级 A）。
- **原理**：把"架构/门禁/熵管理"现状量化成可交付结论，而非口头状态。
- **逻辑**：记分卡（架构约束/质量门禁/覆盖率/熵管理/文档新鲜度等）+ 整改表 + 分文档评估 + 结论；随每次演进刷新（已随 L0/L1/L2/前端硬门禁多次更新）。
- **使用**：浏览器打开查看；重大变更后刷新并提交（本仓库纪律：报告单独 commit）。

### `docs/architecture/harness-engineering.md`　[Harness · 本次新增]
- **功能**：基于本项目的 Harness Engineering 架构与原理说明（即本目录同级文档）。
- **原理**：把"驾驭工程"从概念落地为本仓库可对照的实现说明。
- **逻辑**：8 节——定义/理念/分层架构/熵管理闭环 L0→L2/设计原理/实证数据/边界标注/扩展路线；全部对照真实文件。
- **使用**：人类与 Agent 了解全局机制；尚未提交（待确认）。

---

## 二、项目配置与构建

### `pom.xml`　[脚手架]
- **功能**：Maven 多模块构建根配置（Spring Boot 2.5.15 / Java 8）。
- **原理**：统一依赖与插件；`jacoco-maven-plugin` 在 `verify` 阶段 `check` 执行覆盖率门禁。
- **逻辑**：`<modules>` 含 6 个 ruoyi-*；`jacoco:check` 的 `includes` 与 `enforcement.yml` 的 `coverage-gate` 严格一致（service.impl 100% / biz.domain 55% / biz 60% / common.utils 85%）。
- **使用**：`mvn clean verify -DfailIfNoTests=false`（全量+门禁）；`mvn -pl ruoyi-admin -am -Dtest=...`（单模块）。

### `package-lock.json`（根）　[脚手架]
- **功能**：根级 npm 锁（88 字节占位，前端实际锁在 `ruoyi-ui/`）。
- **使用**：`npm ci` 可复现安装参考。

### `ruoyi-ui/package-lock.json`　[脚手架 · gitignored]
- **功能**：前端依赖精确锁（gitignored，npm 生成）。
- **使用**：`npm install`/`npm ci` 保证依赖一致；删除需重下（清构建产物时保留）。

### `ry.bat` / `ry.sh`　[脚手架]
- **功能**：一键打包脚本（Windows / Linux）。
- **使用**：双击或 `bash ry.sh` 直接出包。

### `.gitignore`　[脚手架 · 已演进]
- **功能**：忽略构建/依赖/IDE/OS 产物。
- **逻辑**：含 `target/`、`node_modules/`、`ruoyi-ui/dist/`、`*.log`、`.idea`、`.DS_Store` 等；本会话清构建产物即依赖此处规则确认"可删"。
- **使用**：Git 自动忽略；无需手动维护（除非新增产物类型）。

### `bin/`　[脚手架]
- **功能**：3 个 `.bat` 启动/运维脚本。
- **使用**：按文件名调用对应运维动作。

---

## 三、后端业务模块（依赖分层：common0→system/quartz/generator1→framework2→admin3）

### `ruoyi-common/`　[脚手架 · layer 0]
- **功能**：无状态工具/常量/注解/异常/基类；含 `com.ruoyi.common.utils`（Arith/StringUtils/DateUtils/SqlUtil/uuid/sign/html，**覆盖率门禁子集 85%**）。
- **铁律**：不得依赖任何 ruoyi 模块、不得写业务逻辑；**禁 Lombok**（getter/setter 手写 + serialVersionUID）。
- **使用**：其他模块复用；新增纯工具须补 `common.utils` 子集单测。

### `ruoyi-system/`　[脚手架 · layer 1]
- **功能**：核心业务层（RuoYi 自带 `com.ruoyi.system.*` + 自有 `com.ruoyi.biz.domain/service/mapper`）。
- **原理**：自有业务按层落位此处（domain/mapper/service/service.impl）。
- **使用**：新增业务实体/服务按 `CLAUDE.md` 落位表放置；`com.ruoyi.system.*` 仍无量级单测（未纳门禁）。

### `ruoyi-framework/`　[脚手架 · layer 2]
- **功能**：框架层（Security/JWT/Web 配置），依赖 system。
- **使用**：业务模块经 admin 间接依赖，勿直接引。

### `ruoyi-quartz` / `ruoyi-generator`　[脚手架 · layer 1]
- **功能**：定时任务模块 / **代码生成器**（反向根据表出 CRUD）。
- **逻辑**：`ruoyi-generator` 默认生成进 `com.ruoyi.system`——自动编码时需重定向到 `com.ruoyi.biz` 并补 `@PreAuthorize`/测试。

### `ruoyi-admin/`　[脚手架 · layer 3]
- **功能**：唯一可执行模块，所有 Controller 落此处 `com/ruoyi/biz/controller/`。
- **使用**：新增 Controller 必须每法 `@PreAuthorize`；架构测试 `ArchitectureRulesTest`/`MapperXmlRulesTest` 在此跑。

### 业务参考实现 `com.ruoyi.biz`（SysProduct / SysStudent）　[Harness · 克隆样板]
- **功能**：自有业务包的成品范例，自动编码"克隆填空"的源。
- **原理**：`SysProductController` 展示标准 CRUD + 权限 + 日志 + Excel 导出套路；`SysStudent` 同构。
- **逻辑**（以 `SysProductController.java` 实读）：`@RestController @RequestMapping("/biz/product")`；list/export/query/add/edit/remove 六法各带 `@PreAuthorize("@ss.hasPermi('biz:product:list|export|query|add|edit|remove')")`；`@Log` 标注操作类型；`deleteSysProductByProductIds` 含树形删除子节点校验（DEBT-002 修复）。
- **使用**：新增教师管理等模块时，照抄此结构，仅改实体/权限串/表名。

---

## 四、前端模块 `ruoyi-ui/`（Vue 2.6.12 + Element UI 2.15 + vue-cli 4，Options API 禁 Vue3）

### `src/api/biz/`　[Harness 落位]
- **功能**：业务模块前端 API（`teacher.js` 等，按 `CLAUDE.md` 落位）。
- **使用**：页面 `import` 后调用；对应后端 `com.ruoyi.biz` 接口。

### `src/views/biz/`　[Harness 落位]
- **功能**：业务页面（教师管理等，Options API + Element UI）。
- **逻辑**：照 `src/views/biz` 下 SysProduct 同构页面（el-table + el-form in dialog + 查询 + `v-hasPermi` + `getDicts`）。
- **使用**：Figma 设计稿 → 截图/Dev Mode → Agent 出 SFC 草稿 → 按约定打磨。

### `src/utils/` + `tests/`　[Harness]
- **功能**：纯函数工具（validate/auth/ruoyi/jsencrypt/permission/errorCode）+ jest 测试。
- **原理**：前端测试只覆盖 `src/utils` 纯函数（75 用例全绿，98.78%）；`.vue` 行为不在此门禁。
- **逻辑**：`jest.config.js` 的 `collectCoverageFrom` 精确列举 5 文件（errorCode.js 静态字典不计入）；`babel.config.js` 复用。
- **使用**：`cd ruoyi-ui && npm test -- --coverage`；`frontend-test` job 为 PR 硬门禁。

### `src/views/system/` + 框架目录　[脚手架]
- **功能**：RuoYi 自带页面（禁改）；框架源码。
- **使用**：勿改 `views/system/**`、`api/system/**`、`common/**`。

### `babel.config.js` / `jest.config.js` / `package.json`　[脚手架/Harness]
- **功能**：构建/测试配置；jest 接入 Vue2 + babel。
- **使用**：新增前端纯函数测试时扩 `jest.config.js` 的 `collectCoverageFrom`。

---

## 五、文档与规范 `docs/`

### `docs/architecture/overview.md` / `boundaries.md`　[脚手架/Harness]
- **功能**：系统架构总览 / 模块边界与依赖规则（boundaries.md 第 5 节 = ArchUnit 规则清单）。
- **使用**：了解架构与边界时读；改架构约束时同步。

### `docs/conventions/README.md` / `testing.md`　[脚手架/Harness]
- **功能**：编码规范 / 测试规范（批次推进：架构→Service→Mapper→common.utils）。
- **使用**：写代码/补测试前对照。

### `docs/plans/current-sprint.md`　[脚手架]
- **功能**：当前迭代任务（任务分解落点）。
- **使用**：每轮迭代计划写入此处。

### `docs/reference/api-spec.yaml` / `error-codes.md`　[脚手架]
- **功能**：API 规范 / 错误码。
- **使用**：对接前后端接口、错误码定义时查。

### `docs/CHANGELOG.md`　[脚手架 · 用户硬性约定]
- **功能**：每次增改功能的变更记录（原功能/更新后功能/日期+版本/模块/原因/影响/回归点，倒序）。
- **使用**：**每次更新功能必须保存变更记录**；本会话新增 `harness-engineering.md` 尚未补此条目（待确认）。

---

## 六、业务数据 `sql/`
- **功能**：建表/初始化 SQL（2 个 `.sql`）。
- **原理**：新表 DDL 应入此处，保证新环境可初始化。
- **已知欠账**：`sys_product`/`sys_student` 建表 SQL 未纳入 `sql/`，新环境初始化会失败——自动编码教师管理时应把 `teacher.sql` 写入此处以修复该缺口。
- **使用**：部署/初始化时执行。

---

## 七、其他根文件
- `README.md` / `LICENSE`　[脚手架]：项目说明与许可。
- `doc/`（1 个 `.docx`）　[脚手架]：需求文档（如项目管理软件需求）。
- `.workbuddy/`　[Harness · 项目数据]：Agent 跨会话记忆（`memory/YYYY-MM-DD.md` + `MEMORY.md`）+ automations；**系统规则禁止删除**。

---

## 八、范围与待办标注
- **未创建（规划中）**：`modules/teacher.yaml`（模块规格草稿）、`scripts/scaffold-module.py`（脚手架生成器）、`scripts/gen-behavior-tests.py`（行为测试生成器）、`sql/teacher.sql`、`src/views/biz/teacher/*`、`com.ruoyi.biz` 的 Teacher/TeacherType 全套后端文件——均为前几轮讨论的"教师管理自动编码"蓝图，尚未落地。
- **本次会话已落地**：清 7 个 `target/` + `ruoyi-ui/coverage`（构建产物）；新增 `docs/architecture/harness-engineering.md`（未提交）；多次刷新合规评估报告（已提交 `eee4002`/`3264ed0`）。
