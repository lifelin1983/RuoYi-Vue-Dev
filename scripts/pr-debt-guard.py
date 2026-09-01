#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PR 债务护栏（Level 2 自动巡检，零依赖：仅标准库，GitHub Actions 直接 python3 可跑）。

定位：在 PR 路径上提前暴露熵增，而非等 nightly 才捕获。
  - 复用 nightly 已回写的 measured_baseline（enforcement.yml，base/main 实测真相）作 diff 基准
  - 对比 PR head 的：
      * com.ruoyi.biz.domain 整包 LINE%（是否跌破 base 基线 → 覆盖率退化）
      * com.ruoyi.biz.service.impl 下是否有新增未测类（new_untested_class）
  - 命中则生成 PR 评论 Markdown（含可一键采纳的 DEBT 草稿 YAML）；告警非阻断（exit 0）。

为什么不自动写台账文件：
  - fork PR 的 head 分支在 fork 仓库，GITHUB_TOKEN 无写权；
  - 自动写 base 仓库文件属供应链风险，且会与 Gate 2 硬门禁重复阻断。
  → 草稿以评论代码块形式贴出，人类采纳时粘贴到 .harness/harness-debt.yaml 并改名编号。

口径严格对齐：
  - load_package_line_coverage / find_untested_classes / class_has_test
    与 scripts/audit-harness-debt.py Level 1 逻辑一致（同源，避免双份语义漂移）。

退出码：始终 0（告警型，不阻断 PR）。是否发评论由 CI step 依 --out 文件是否存在决定。
"""
import argparse
import csv
import glob
import os
import re
import sys
from datetime import date

# ---- 路径定位 ----
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
LEDGER = os.path.join(PROJECT_ROOT, ".harness", "harness-debt.yaml")
ENF = os.path.join(PROJECT_ROOT, ".harness", "enforcement.yml")

DOMAIN_PKG = "com.ruoyi.biz.domain"
IMPL_PKG = "com.ruoyi.biz.service.impl"


# ---------- 以下函数与 audit-harness-debt.py Level 1 同源（复制以保持脚本独立零依赖） ----------

def load_package_line_coverage(pkg_prefix, jacoco_dir=None):
    """从 jacoco.csv 实算指定包前缀的整包 LINE%（百分比数值）。
    jacoco_dir 指定时仅在该目录树内搜索（CI 从 artifact 下载到 downloaded/）。
    找不到或该包无数据 → 返回 None（调用方应 SKIP）。"""
    if jacoco_dir:
        files = glob.glob(os.path.join(jacoco_dir, "**", "jacoco.csv"), recursive=True)
    else:
        files = glob.glob(os.path.join(PROJECT_ROOT, "**", "target", "site", "jacoco", "jacoco.csv"),
                          recursive=True)
    if not files:
        files = glob.glob(os.path.join(PROJECT_ROOT, "**", "jacoco.csv"), recursive=True)
    if not files:
        return None
    lc = lm = 0
    found = False
    for f in files:
        try:
            with open(f, newline="", encoding="utf-8") as fh:
                r = csv.reader(fh)
                h = next(r)
                I = {k: i for i, k in enumerate(h)}
                P = h.index("PACKAGE")
                for row in r:
                    if not row[P].startswith(pkg_prefix):
                        continue
                    ic = int(row[I["LINE_COVERED"]])
                    im = int(row[I["LINE_MISSED"]])
                    if ic + im == 0:
                        continue
                    lc += ic
                    lm += im
                    found = True
        except (OSError, ValueError, IndexError):
            continue
    if not found:
        return None
    if lc + lm == 0:
        return 0.0
    return lc / (lc + lm) * 100.0


def class_has_test(short, test_names):
    variants = {
        short + "Test",
        short + "ImplTest",
        short + "ServiceImplTest",
        short + "MapperTest",
        short + "ControllerTest",
        "I" + short + "Test",
    }
    return bool(variants & test_names)


def find_untested_classes(pkg):
    """列出 pkg 下所有源码业务类，返回其中没有对应测试类的短名列表。"""
    pkg_path = pkg.replace(".", os.sep)
    src_classes = []
    for mod in ("ruoyi-system", "ruoyi-common", "ruoyi-admin",
                "ruoyi-framework", "ruoyi-quartz", "ruoyi-generator"):
        base = os.path.join(PROJECT_ROOT, mod, "src", "main", "java", pkg_path)
        if not os.path.isdir(base):
            continue
        for fn in sorted(os.listdir(base)):
            if fn.endswith(".java"):
                src_classes.append(fn[:-5])
    if not src_classes:
        return []
    test_names = set()
    for root, _d, files in os.walk(PROJECT_ROOT):
        if "target" in root.split(os.sep):
            continue
        if "src/test" not in root.replace(os.sep, "/"):
            continue
        for fn in files:
            if fn.endswith("Test.java"):
                test_names.add(fn[:-5])
            elif fn.endswith(".java") and "Test" in fn:
                test_names.add(fn[:-5])
    return [s for s in src_classes if not class_has_test(s, test_names)]


def read_domain_baseline(path):
    """从 enforcement.yml 的 measured_baseline 解析 com.ruoyi.biz.domain 整包基线百分比。"""
    try:
        with open(path, encoding="utf-8") as f:
            txt = f.read()
    except OSError:
        return None
    m = re.search(r'com\.ruoyi\.biz\.domain:\s*"([^"]*)"', txt)
    if not m:
        return None
    mm = re.search(r'整包\s*([\d.]+)\s*%', m.group(1))
    return float(mm.group(1)) if mm else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pr", default="", help="PR 编号（仅用于草稿标注）")
    ap.add_argument("--jacoco-dir", default=None,
                    help="jacoco.csv 所在目录（CI 从 artifact 下载）。默认 PROJECT_ROOT 递归")
    ap.add_argument("--out", default="coverage-debt-guard.md",
                    help="命中时写出的 PR 评论 Markdown 路径")
    args = ap.parse_args()

    domain_cur = load_package_line_coverage(DOMAIN_PKG, args.jacoco_dir)
    domain_base = read_domain_baseline(ENF)
    untested = find_untested_classes(IMPL_PKG)

    today = date.today().isoformat()
    issues = []
    draft_blocks = []

    # 1) domain 覆盖率退化
    # 退化容差 0.5pp：head 实算值（如 66.6666…）与 base 声明值（66.67）存在浮点/测量
    # 噪声，若不设容差会恒定误报"退化"。仅当明显下降（>0.5pp）才告警。
    DEGRADE_TOL_PP = 0.5
    if (domain_cur is not None and domain_base is not None
            and domain_cur < domain_base - DEGRADE_TOL_PP):
        diff = domain_base - domain_cur
        issues.append(
            "### ⚠️ domain 覆盖率退化\n"
            "- PR head 整包 **%.2f%%** < base 基线 **%.2f%%**（跌破 measured_baseline，下降 %.2fpp）\n"
            "- 建议：补齐 `com.ruoyi.biz.domain` 测试，或在 PR 说明下降原因" % (
                domain_cur, domain_base, diff))
        draft_blocks.append(
            "  - id: DEBT-DRAFT-%s-1   # 采纳后改名 DEBT-0xx\n"
            "    kind: process\n"
            "    title: PR #%s 引入 domain 覆盖率退化（head %.2f%% < base %.2f%%）\n"
            "    priority: P1\n"
            "    status: draft\n"
            "    verifiable: true\n"
            "    check:\n"
            "      type: coverage_delta\n"
            "      package: %s\n"
            "      baseline: %.2f\n"
            "      tolerance: 0\n"
            "    created: %s\n"
            "    source: PR #%s 自动草稿（待人工采纳改名）" % (
                today.replace("-", ""), args.pr or "?", domain_cur, domain_base,
                DOMAIN_PKG, domain_cur, today, args.pr or "?"))

    # 2) service.impl 新增未测类
    if untested:
        issues.append(
            "### ⚠️ service.impl 新增未测类\n"
            "- 以下类无对应测试类（命名变体均未命中）：%s\n"
            "- 建议：为上述类补 `*Test`，否则不予合入（enforcement.yml rule）" % ", ".join(untested))
        draft_blocks.append(
            "  - id: DEBT-DRAFT-%s-2   # 采纳后改名 DEBT-0xx\n"
            "    kind: process\n"
            "    title: PR #%s 在 service.impl 引入未测类（%s）\n"
            "    priority: P1\n"
            "    status: draft\n"
            "    verifiable: true\n"
            "    check:\n"
            "      type: new_untested_class\n"
            "      package: %s\n"
            "    created: %s\n"
            "    source: PR #%s 自动草稿（待人工采纳改名）" % (
                today.replace("-", ""), args.pr or "?", ", ".join(untested),
                IMPL_PKG, today, args.pr or "?"))

    if not issues:
        print("[pr-debt-guard] 干净：domain 覆盖率未退化，service.impl 无新增未测类。")
        return 0

    header = (
        "## 🔍 Harness 债务护栏（Level 2 自动巡检）\n\n"
        "本 PR 触发了以下熵管理告警，建议处理后合入（**告警非阻断**，门禁仍由 Gate 2 兜底）：\n\n"
    )
    draft_section = (
        "\n---\n\n### 📋 可一键采纳的 DEBT 草稿\n"
        "将以下片段粘贴至 `.harness/harness-debt.yaml`（改 `DEBT-DRAFT-*` 为正式 `DEBT-0xx` 编号）即可纳入 nightly 自动巡检：\n\n"
        "```yaml\n" + "\n".join(draft_blocks) + "\n```\n"
    )
    comment = header + "\n".join(issues) + draft_section + (
        "\n\n> 本评论由 `scripts/pr-debt-guard.py` 自动生成。"
    )
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(comment)
    print("[pr-debt-guard] 命中 %d 项告警，已写出评论：%s" % (len(issues), args.out))
    for b in issues:
        print("  - " + b.splitlines()[0])
    return 0


if __name__ == "__main__":
    sys.exit(main())
