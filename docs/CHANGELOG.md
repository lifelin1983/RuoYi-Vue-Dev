# 变更记录（CHANGELOG）

> 约定：每次功能更新必须记录。格式为「原功能 → 更新后功能」对比，附日期+版本、涉及模块、变更原因、影响范围与回归点。
> 时间倒序，最新在上。

---

## 2026-08-31 · v3.9.2-harness-p1 · JaCoCo 覆盖率门禁接入

- **涉及模块**：`pom.xml`（父 POM）、`.github/workflows/ci.yml`、`/enforcement.yml`、`.claude/CLAUDE.md`、`Harness-Engineering-合规评估报告.html`
- **原功能**：编译期 `-am` 反应堆 + ArchUnit 架构约束（本地 `mvn test` 强制）；覆盖率仅为 `enforcement.yml` 里的 `coverage_targets`「目标值」，无机器强制。
- **更新后功能**：
  - 父 POM 注入 `jacoco-maven-plugin`（prepare-agent + report + **check** 三 goal，全模块继承）。
  - 门禁**仅覆盖老大自写代码 `com.ruoyi.biz` 包**（棘轮基线，只升不降）：
    - `com.ruoyi.biz.service.impl.*` → LINE COVEREDRATIO ≥ **1.00**（硬门槛）
    - `com.ruoyi.biz.domain.*` → LINE COVEREDRATIO ≥ **0.40**
    - `com.ruoyi.biz.*`（BUNDLE）→ LINE COVEREDRATIO ≥ **0.60**
  - CI Gate 2b 由 `clean test` 改为 `clean verify`，并执行 JaCoCo `check`；产物新增 `jacoco-reports` artifact 上传。
  - RuoYi 核心（`com.ruoyi.system.*`）与 `common.utils` 暂无量级单测，**未纳入门禁**，以免打红当前全绿 CI；待补单测后扩大 `includes`。
  - `mapper` 接口由 MyBatis 动态代理生成，无字节码可被插桩，JaCoCo 无法测量，靠 Mapper 测试存在性兜底。
- **变更原因**：Harness Engineering 第二支柱（架构约束）要求把"目标"变为"不可合入的硬门槛"；覆盖率门禁是质量门禁层的关键一环。
- **影响范围**：所有 `mvn verify`（含 CI）多一道 `jacoco:check`；`com.ruoyi.biz` 下新增/修改类若无测试覆盖，`verify` 直接 BUILD FAILURE。
- **验证 / 回归点**：
  - ✅ 正向：本地 `mvn -B clean verify -DfailIfNoTests=false` → BUILD SUCCESS（"All coverage checks have been met"）。
  - ✅ 反向：临时塞一个无覆盖的 `com.ruoyi.biz.service.impl.TempProbeServiceImpl`，`verify` 立即 BUILD FAILURE（jacoco:check 报 rule violated: lines covered ratio 0.00 < 1.00）；删除后恢复 SUCCESS。
  - ⚠️ 回归注意：`-Dspring-boot.repackage.skip=true` 仅用于本地跳过慢速 buildpack 下载，CI 不跳（需生成可执行 jar）。
  - ⚠️ 代理环境：`settings.xml` 已配系统代理 `127.0.0.1:3213` + `pom.xml` 中央仓库改 `repo1.maven.org`，否则公司零信任 DNS 劫持导致依赖下载 127.0.1.2 失败。

---

## 2026-08-31 · v3.9.2-harness-p0 · 文档新鲜度校验 + CI 闸门

- **涉及模块**：`scripts/check-doc-links.sh`、`.github/workflows/ci.yml`、`.harness/enforcement.yml`、`.claude/CLAUDE.md`、`docs/*`
- **原功能**：架构约束仅本地 `mvn test` 强制；文档引用无自动化巡检；"全仓库零测试"等过期表述残留。
- **更新后功能**：
  - `scripts/check-doc-links.sh`（6 类检查，`--strict`）实现并接入 CI Gate 1，36 项通过 / 0 error / 0 warning。
  - `ci.yml` 双并行闸门（Gate 1 文档新鲜度 + Gate 2 架构约束/全量测试 + Redis service）。
  - 推送 GitHub（私有 `RuoYi-Vue-Dev`，main），Actions run #33380657399 三道闸门全绿。
- **影响范围**：PR 合入前强制跑文档漂移 + 架构约束 + 全量测试。
- **验证 / 回归点**：`mvn test -pl ruoyi-admin -am -Dtest=ArchitectureRulesTest,MapperXmlRulesTest -DfailIfNoTests=false` 必带 `-DfailIfNoTests=false`（否则无匹配模块报 "No tests were executed!"）。
