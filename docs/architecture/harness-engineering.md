# Harness Engineering 在本项目的架构与原理

> 一份"基于本项目真实落地"的说明，而非通用概念搬运。
> 所有结论均可对照仓库内文件核验：`.claude/CLAUDE.md`、`.harness/enforcement.yml`、
> `.harness/harness-debt.yaml`、`scripts/*.py`、`.github/workflows/ci.yml`、`scripts/git-hooks/pre-commit`。

---

## 0. 一句话定义

**Harness Engineering（驾驭工程）= 给代码库套上"机器可读、机器强制"的缰绳（harness），
让质量与架构约束不依赖人的自觉、也不依赖 Agent 的"听话程度"。**

本项目的核心信条（写在 `enforcement.yml` 开头）：

> **Agent failures are harness failures.**
> 当 Agent 违反规则时，先补这里的约束，而不是去改 Agent 的提示词。

换句话说：把"好好写代码"从**对人的要求**变成**对系统的强制**。

---

## 1. 核心理念：为什么叫"驾驭"而非"规范"

传统做法：写一份 CONTRIBUTING.md / 架构文档 → 期待人和 AI 都照做。
问题：文档会漂移、人会忘、Agent 会照着过时的文档干。

Harness 的做法：把约束写成**机器能吃、能吃后能拦**的东西——

| 传统"规范" | Harness"缰绳" |
|---|---|
| 文档说"Controller 必须带 @PreAuthorize" | ArchUnit 规则在 `mvn test` 直接 FAIL，且 PR 合入被 Gate 2 阻断 |
| 文档说"覆盖率不能下降" | JaCoCo `jacoco:check` 硬门禁 + nightly 自动回写基线（棘轮） |
| 文档说"记得补测试" | 债务台账 `harness-debt.yaml` 由 nightly 自动巡检，假绿会被记 drift |

**本质区别**：规范靠"读"，缰绳靠"跑"。本项目的每一道约束都有对应的可执行 check。

---

## 2. 架构总览：四层 + 仓库保护

```
┌─────────────────────────────────────────────────────────────┐
│ ④ 仓库保护 (branch protection)                                │
│    required_status_checks = [Gate 2, Gate 1, Frontend]       │
│    allow_force_pushes=false · allow_deletions=false          │
│    → PR 合入须三闸门全绿；历史不可被改写/误删                   │
└───────────────────────────────┬─────────────────────────────┘
                                 │ 阻断
┌───────────────────────────────┴─────────────────────────────┐
│ ③ CI/CD 闸门层 (.github/workflows/ci.yml)                     │
│   Gate 1 docs-freshness │ Gate 2 build-and-test │            │
│   frontend-test │ nightly-sync-baseline │ nightly-audit-debt │
│   pr-debt-guard (Level 2)                                      │
└───────────────────────────────┬─────────────────────────────┘
                                 │ 调用
┌───────────────────────────────┴─────────────────────────────┐
│ ② 强制引擎层 (可复用、零依赖)                                 │
│   ArchUnit 1.3.0  (架构约束)                                  │
│   JaCoCo        (覆盖率门禁，verify 阶段)                     │
│   Python 巡检    (audit-harness-debt / pr-debt-guard /        │
│                  sync-coverage-baseline，仅标准库)            │
└───────────────────────────────┬─────────────────────────────┘
                                 │ 读
┌───────────────────────────────┴─────────────────────────────┐
│ ① 真相源层 (single source of truth)                           │
│   .claude/CLAUDE.md      Agent 行为指南（五条铁律）            │
│   docs/                  自然语言规则（架构/规范/API/错误码）  │
│   .harness/enforcement.yml   机器可读约束声明（门禁硬配置）    │
│   .harness/harness-debt.yaml  熵追踪台账（债务唯一真相源）    │
└─────────────────────────────────────────────────────────────┘

另有 ④ 之外的"本地护栏"：scripts/git-hooks/pre-commit 把 Gate 1 提前到
"提交即拦"（秒级、离线、不依赖 Maven/网络）。
```

> 内联可视化见会话内两张图：**分层架构图** 与 **熵管理闭环图**。

---

## 3. 四层组件详解

### 3.1 真相源层（唯一事实源）
- **`.claude/CLAUDE.md`**：给 Agent 的"宪法"。五条铁律（实体加字段改三处、Mapper XML 命名、Controller 必带 @PreAuthorize、common 零业务依赖、业务按层跨模块落位），每条都对应一次真实故障/漏洞。
- **`docs/`**：规则的自然语言版（架构边界、编码规范、测试规范、API、错误码）。`enforcement.yml` 顶部注明"自然语言说明在 docs/，本文件只做结构化声明"。
- **`.harness/enforcement.yml`**：**机器可读的约束声明**（Pillar 2 Architectural Constraints）。含模块分层、banned_imports、naming、security 红线、persistence、test_requirements（覆盖率门槛）、ci_jobs。它是 CI 与 Agent 共同消费的契约。
- **`.harness/harness-debt.yaml`**：**债务/熵的唯一真相源**。原 enforcement.yml 里的 `still_missing` / `resolved_defects` 已迁移至此，消除"双源漂移"——一处改了另一处忘改的经典坑。

### 3.2 强制引擎层（把声明变成失败）
- **ArchUnit 1.3.0**：`ArchitectureRulesTest` + `MapperXmlRulesTest` 把分层、依赖方向、权限注解、命名、Mapper XML 规则编译成测试。违反即 `mvn test` 失败。含**反向测试**证据：临时注入无 @PreAuthorize 的 Controller，规则精确拦截到第 21 行。
- **JaCoCo**：`pom.xml` 的 `jacoco-maven-plugin` 在 `verify` 阶段 `check`，按 `enforcement.yml` 的 `coverage-gate` 口径校验（service.impl 100% / biz.domain 55% / biz 整包 60% / common.utils 子集 85%）。
- **Python 巡检（零依赖，仅标准库）**：`audit-harness-debt.py`、`pr-debt-guard.py`、`sync-coverage-baseline.py`。刻意不引 pyyaml——用轻量行解析，让 GitHub Actions 直接 `python3` 可跑，无依赖安装步骤。

### 3.3 CI/CD 闸门层（不可合入的闸）
`ci.yml` 把本地 `mvn test` 升级为**不可合入的闸门**，五个 job：
1. **Gate 1 · docs-freshness**：`check-doc-links.sh --strict`，秒级、不依赖 JVM，校验文档引用，并识别"文档声称无用例、但源码已有测试"这类自相矛盾（stale-claim）。最便宜、收益最高，故独立且前置。
2. **Gate 2 · build-and-test**：`Gate 2a` 架构约束 + `Gate 2b` 全量测试 + 覆盖率门禁（需 Redis service 给 Controller 鉴权测试）。`haltOnFailure=true`，不达标直接 BUILD FAILURE。随后聚合 jacoco.csv 生成徽章 + 对 PR 发覆盖率评论。
3. **frontend-test**：jest 跑 `ruoyi-ui/tests/`（75 用例全绿，98.78%）。**已加入 main 分支 `required_status_checks` contexts，PR 合入硬门禁**。
4. **nightly-sync-baseline**：每天 02:00（北京）重跑，实算四个门禁目标真实覆盖率，回写 `enforcement.yml` 的 `measured_baseline`（观测事实自动同步）。
5. **nightly-audit-debt + pr-debt-guard**：熵管理主线（见第 4 节）。

### 3.4 本地护栏 + 仓库保护
- **pre-commit 钩子**：把 Gate 1 提前到"提交即拦"（离线、秒级）。`SKIP_DOC_CHECK=1` 可临时跳过，`--no-verify` 完全绕过（不推荐）。架构与覆盖率门禁仍在 CI 强制，本地钩子不重复跑它们以保持提交流畅。
- **branch protection**：`allow_force_pushes=false` + `allow_deletions=false`（防历史被篡改/误删）；`required_status_checks`（strict=false）使 PR 合入须 Gate1+Gate2+Frontend 三道全绿。

---

## 4. 核心原理：熵管理闭环（L0 → L1 → L2）

Harness Engineering 的本质是**熵管理**：技术债务/约束漂移的总量必须被持续观测、暴露、回收，而不是静默增长。

本项目的台账（`harness-debt.yaml`）按能力演进分三级：

```
L0（已落地）  结构化台账 + nightly 校验 verifiable 项
   │          验证型 check：test_exists —— 声明 resolved，但测试被误删/改名 → 记 drift
   ▼
L1（已落地）  监控型 check：coverage_delta / new_untested_class
   │          持续有效，FAIL 即记 drift；恢复 PASS 自动撤销 drift（回到 open）
   │          无 jacoco.csv 时 coverage_delta 降级 SKIP，不误报
   ▼
L2（已落地）  PR 债务护栏（pr-debt-guard.py）
              PR 路径上提前暴露熵增，生成"可一键采纳的 DEBT 草稿"评论；告警非阻断
```

**闭环如何转**：
1. **观测**：nightly 跑 `audit-harness-debt.py`，对每条 `verifiable:true` 的项实跑 check（读 `src/test` 源码 + 聚合 `jacoco.csv`）。
2. **比对**：检查证据是否仍成立（验证型）/ 是否跌破红线（监控型）。
3. **漂移**：失效或跌破 → 脚本把 `status` 改写为 `drift`，写 `drift_note`（`--rewrite`），随后 CI 提交推送。
4. **恢复**：证据恢复 → 自动撤销 `drift` 回到 `open`，删 `drift_note`。
5. **基线同步**：`nightly-sync-baseline.py` 把实测覆盖率回写 `measured_baseline`，让"观测事实"自动跟上代码，避免人工声明滞后（漂移的根因）。
6. **PR 提前量**：`pr-debt-guard.py` 在 PR 阶段就对比 head 与基线，比等 nightly 更早暴露退化，并贴出可粘贴采纳的 DEBT 草稿。

> 关键：nightly/PR 脚本**不自动写台账文件**（`pr-debt-guard` 只发评论草稿），原因有二——
> fork PR 的 head 在 fork 仓库、token 无写权；自动写 base 仓库属供应链风险且与 Gate 2 重复阻断。

---

## 5. 关键设计决策与背后原理

### 5.1 棘轮效应（ratchet）+ 基线自动回写
覆盖率门槛**只升不降**。但门槛之上的"实测基线"曾长期停留在旧值（domain 声明 ~50%，实测已 66.67%）——人工维护必然滞后。
**根治**：让 nightly 用 `sync-coverage-baseline.py` 从 `jacoco.csv` 实算并回写 `measured_baseline`。
**原理**：策略归人（门槛 `coverage_targets` 人工维护），观测归机器（`measured_baseline` 自动同步）。

### 5.2 验证型 vs 监控型 check（语义分离）
- **验证型**（`test_exists`）：仅当声明 `resolved/mitigated` 时校验证据；证据失效才记 drift。用于"曾经修过的缺陷会不会被悄悄弄丢"。
- **监控型**（`coverage_delta` / `new_untested_class`）：持续有效，FAIL 即记 drift，恢复自动撤销。用于"覆盖率/测试配对是否在静默退化"。
两类语义不同，脚本分别处理（`MONITOR_TYPES` 常量区分）。

### 5.3 浮点容差防误报
`DEBT-005` 基线 66.00% 比实测 66.67% 留 **0.67pp** 余量；`pr-debt-guard` 退化容差 **0.5pp**。
**原理**：head 实算值（如 66.6666…）与 base 声明值（66.67）存在浮点/测量噪声，不设容差会恒定误报"退化"。仅当明显退化（>阈值）才告警。

### 5.4 文档即真相源 → 最便宜闸门前置
"文档漂移 = Agent 照着错的干"。因此 Gate 1（docs-freshness）成本最低、收益最高，独立成 job 且**不依赖 JVM**；并进一步前置到 pre-commit 钩子（提交即拦）。
**原理**：约束的"第一道闸"应该最快、最便宜、最容易被触发，才能把错误挡在最早。

### 5.5 策略归人、观测归机器
`enforcement.yml` 里 `coverage_targets`（门槛阈值）人工维护，`measured_baseline`（实测值）机器回写。徽章聚合脚本精确镜像 `pom.xml` 的 `jacoco:check` includes（同一口径），避免被未测试框架代码稀释而显示误导性低值。

### 5.6 渐进式门禁（报告型 → 硬门禁）
先"报告型"（job 失败仅界面可见、不阻断），验证稳了再升级为"硬门禁"（加入 `required_status_checks`）。本项目 frontend-test 已完成此升级；直推 main 路径仍维持报告型（见第 7 节边界）。

### 5.7 提交卫生（跨项目纪律）
代码 / 报告 / memory 分开提交；nightly 自动提交加 `[skip ci]` 防递归触发；日志类改动不与功能改动混进同一 commit（否则 `git revert` 会连带撤销历史记录）。

---

## 6. 本项目实证数据（原理不是空谈）

| 维度 | 实测值 |
|---|---|
| 后端测试 | 154 用例全绿（架构 16 + Service + Mapper + Controller + common.utils 86） |
| 门禁守护范围行覆盖率 | ~84%（徽章口径，仅统计受门禁类，与 pom 严格一致） |
| service.impl | 100%（SysProduct / SysStudent） |
| biz.domain 整包 | 66.67%（逐类 SysProduct 58.8% / SysStudent 71.0%） |
| biz 整包 | 78.08% |
| common.utils 子集 | 85.04%（Arith 100% / Base64 94.9% / SqlUtil 94.1% …） |
| 前端测试 | 75 用例全绿，行覆盖率 98.78%（auth/jsencrypt/permission 100%） |
| 模块分层 | common(0) → system/quartz/generator(1) → framework(2) → admin(3)，禁止反向与成环 |
| 安全红线 | @PreAuthorize 必带 · 禁 raw SQL（过 SqlUtil）· 禁 Lombok |
| 熵台账 | DEBT-001~006；L0/L1 已自动巡检，L2 PR 护栏已上线 |

**反向测试证据**（证明门禁真生效，非假绿）：
- 临时将 service.impl 门槛 1.00→0.50，verify 仍 SUCCESS；恢复 1.00 并删一个单测后 verify 转 FAILURE。
- 临时注入无 @PreAuthorize 的 Controller，ArchUnit 精确拦截到 `TempUnsecuredController.leak()` 第 21 行。

---

## 7. 边界与诚实标注（已知局限）

- **直推 main 不受 `required_status_checks` 约束**：该配置只约束 PR 合入，普通 `git push` 不受阻。个人账号仓库不支持 `restrictions`（GitHub 422: 仅 Org 仓库可设 user/team 限制），故"彻底禁直推"当前不可原生实现，已决策维持现状（直推路径为报告型）。若将仓库转入 Org，该开关即可解锁。
- **基线曾长期漂移**：domain 声明 ~50% 与实测 66.67% 不符，已于 2026-09-01 用 `jacoco.csv` 实算同步，并由 nightly 回写根治。
- **RuoYi 核心 `com.ruoyi.system.*` 仍无量级单测**，未纳入门禁（避免打红 CI）；待补单测后继续扩大 includes。
- **Mapper 层 JaCoCo 无法测量**（MyBatis 动态代理），靠"Mapper 测试存在性"兜底（`SysProductMapperTest` 等）。
- **`errorCode.js` 覆盖率 0% 是 istanbul 假象**（纯静态字典不插桩），已用 `errorCode.spec.js` 覆盖查表正确性，并从 `collectCoverageFrom` 剔除。
- **`field-sync-required`（实体加字段改三处）暂无法自动化**，靠 Code Review 清单（`enforcement: manual_review`）。

---

## 8. 如何扩展（给后续迭代）

1. **扩 coverage includes**：补 `com.ruoyi.system.*` 单测后，将其加入 `pom.xml` 与 `enforcement.yml` 的 `coverage-gate`，门槛按棘轮渐进上调。
2. **加更多监控型 check**：`harness-debt.yaml` 的 schema 已预留（`coverage_delta` / `new_untested_class` 之外可加新 `type`，在 `audit-harness-debt.py` 的 `run_check` 加分支即可）。
3. **L2 升级为阻断**（若团队接受）：把 `pr-debt-guard.py` 的 exit 0 改为 exit 1 并接入 `required_status_checks`（需先解决 fork PR 写权/供应链顾虑）。
4. **文档约束结构化**：把 `docs/` 里的更多"应做/不应做"迁移为 ArchUnit 或 `check-doc-links.sh` 可校验项，让"规范"持续转化为"缰绳"。

---

*文档生成依据：仓库内 `.claude/CLAUDE.md`、`.harness/enforcement.yml`、`.harness/harness-debt.yaml`、
`scripts/audit-harness-debt.py`、`scripts/pr-debt-guard.py`、`scripts/sync-coverage-baseline.py`、
`.github/workflows/ci.yml`、`scripts/git-hooks/pre-commit` 的实测内容与注释。*
