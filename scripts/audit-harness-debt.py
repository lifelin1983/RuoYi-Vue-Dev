#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Harness 债务台账自动巡检（零依赖：仅用标准库，GitHub Actions 直接 python3 可跑）。

功能（Level 0）：
  1. 解析 .harness/harness-debt.yaml（轻量行解析，无需 pyyaml）
  2. 对 verifiable:true 的项实跑 check：
       - type: test_exists  校验「测试类 + 方法」是否仍存在于 src/test 源码
  3. 比对声明 status：
       - 声明 resolved/mitigated，但 check 证据已失效 → 漂移
       - 声明 open 且 verifiable:false → 仅信息性告警，不自动改
  4. 漂移时（--rewrite）回写台账：status → drift，并写入 drift_note
  5. 退出码：0=无漂移（健康）/ 2=发现漂移并已回写（供 ci.yml 据此 push）/ 1=脚本错误

用法：
  python3 scripts/audit-harness-debt.py            # 仅报告，不改文件
  python3 scripts/audit-harness-debt.py --rewrite  # 漂移则回写台账并 exit 2
"""
import os
import re
import sys
import glob

# ---- 路径定位：脚本位于 <root>/scripts/，台账位于 <root>/.harness/harness-debt.yaml ----
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
LEDGER = os.path.join(PROJECT_ROOT, ".harness", "harness-debt.yaml")


def parse_ledger(path):
    """轻量行解析 ledger，返回 item 列表（含 _line 起始行号）。"""
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    items = []
    cur = None
    for i, line in enumerate(lines):
        m = re.match(r"^  - id:\s*(\S+)", line)
        if m:
            cur = {"id": m.group(1), "_line": i}
            items.append(cur)
            continue
        if cur is None:
            continue
        v = line.rstrip("\n")
        m = re.match(r"^    status:\s*(\S+)", v)
        if m:
            cur["status"] = m.group(1)
            continue
        m = re.match(r"^    verifiable:\s*(\S+)", v)
        if m:
            cur["verifiable"] = m.group(1).lower() == "true"
            continue
        m = re.match(r"^    kind:\s*(\S+)", v)
        if m:
            cur["kind"] = m.group(1)
            continue
        m = re.match(r"^    priority:\s*(\S+)", v)
        if m:
            cur["priority"] = m.group(1)
            continue
        m = re.match(r"^    title:\s*(.+)", v)
        if m:
            cur.setdefault("title", m.group(1).strip())
            continue
        m = re.match(r"^    check:", v)
        if m:
            cur.setdefault("check", {})
            continue
        m = re.match(r"^      type:\s*(\S+)", v)
        if m and cur.get("check") is not None:
            cur["check"]["type"] = m.group(1)
            continue
        m = re.match(r"^      test:\s*(\S+)", v)
        if m and cur.get("check") is not None:
            cur["check"]["test"] = m.group(1)
            continue
    return lines, items


def test_exists(test_ref):
    """test_ref = 'ClassName.methodName'，校验类与方法是否仍存在于 src/test 源码。"""
    if "." not in test_ref:
        return False, None
    class_name, method_name = test_ref.rsplit(".", 1)
    # 收集所有测试源码
    matches = []
    for root, _dirs, files in os.walk(PROJECT_ROOT):
        if "target" in root.split(os.sep):
            continue
        for fn in files:
            if fn.endswith(".java") and "src/test" in root.replace(os.sep, "/"):
                matches.append(os.path.join(root, fn))
    found_class = None
    for fp in matches:
        try:
            text = open(fp, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        if re.search(r"class\s+%s\b" % re.escape(class_name), text):
            found_class = fp
            if re.search(r"\b%s\s*\(" % re.escape(method_name), text):
                return True, fp
    if found_class:
        # 类在，方法被改名/删除 → 证据失效
        return False, found_class
    return False, None


def run_check(item):
    """返回 (ok: bool, detail: str)。"""
    check = item.get("check") or {}
    ctype = check.get("type")
    if ctype == "test_exists":
        ref = check.get("test", "")
        ok, where = test_exists(ref)
        if ok:
            return True, "test_exists PASS: %s (%s)" % (ref, os.path.relpath(where, PROJECT_ROOT))
        return False, "test_exists FAIL: %s 证据已失效（%s）" % (
            ref,
            "类/方法不存在" if where is None else "类在但方法缺失: " + os.path.relpath(where, PROJECT_ROOT),
        )
    return True, "check type '%s' 未实现，跳过" % ctype


def audit(lines, items, rewrite):
    report = []
    drifted = []
    for it in items:
        vid = it.get("id", "?")
        title = it.get("title", "")
        status = it.get("status", "open")
        verb = it.get("verifiable", False)
        line = ["[%s] %s  (status=%s, verifiable=%s)" % (vid, title, status, verb)]
        if not verb:
            line.append("    → 人工维护项（verifiable:false），仅记录，不自动校验")
            report.append("\n".join(line))
            continue
        if status not in ("resolved", "mitigated"):
            # open/wontfix/drift：不反向改（测试存在不等于缺陷已修，保持保守）
            line.append("    → verifiable 但状态=%s，仅信息性记录，不自动翻转" % status)
            report.append("\n".join(line))
            continue
        ok, detail = run_check(it)
        line.append("    → %s" % detail)
        report.append("\n".join(line))
        if not ok:
            drifted.append(it)
    return report, drifted


def rewrite_ledger(lines, items, drifted):
    """对漂移项回写 status=drift + drift_note。返回是否改动。"""
    if not drifted:
        return lines, False
    drift_ids = {d["id"] for d in drifted}
    # 计算每个 item 的块边界
    bounds = {}
    for idx, it in enumerate(items):
        start = it["_line"]
        end = items[idx + 1]["_line"] if idx + 1 < len(items) else len(lines)
        bounds[it["id"]] = (start, end)

    from datetime import date
    today = date.today().isoformat()
    out = list(lines)
    changed = False
    for d in drifted:
        sid = d["id"]
        start, end = bounds[sid]
        # 找 status 行
        for j in range(start, end):
            if re.match(r"^    status:\s*", out[j]):
                out[j] = "    status: drift\n"
                changed = True
                # 找是否已有 drift_note
                has_note = any(re.match(r"^    drift_note:", out[k]) for k in range(start, end))
                if not has_note:
                    note = (
                        '    drift_note: "%s · nightly 巡检：声明 %s 但 check 证据已失效，'
                        '需人工复核（%s）"\n'
                        % (today, d.get("status", "resolved"), (d.get("check") or {}).get("test", ""))
                    )
                    out.insert(j + 1, note)
                    # 后续边界需 +1（就地修正：简单起见重算后续偏移）
                    for other in items:
                        if other["_line"] > j:
                            other["_line"] += 1
                break
    return out, changed


def main():
    args = sys.argv[1:]
    rewrite = "--rewrite" in args
    if not os.path.exists(LEDGER):
        sys.stderr.write("ERROR: ledger not found: %s\n" % LEDGER)
        return 1
    lines, items = parse_ledger(LEDGER)
    report, drifted = audit(lines, items, rewrite)

    print("=" * 72)
    print("Harness 债务台账巡检  ·  %s" % LEDGER)
    print("=" * 72)
    for block in report:
        print(block)
    print("-" * 72)
    print("总计：%d 项，漂移 %d 项" % (len(items), len(drifted)))
    for d in drifted:
        print("  ⚠ 漂移: %s (%s)" % (d["id"], (d.get("check") or {}).get("test", "")))
    print("=" * 72)

    if not drifted:
        return 0

    if not rewrite:
        print("[report-only] 发现漂移但未指定 --rewrite，不回写台账。")
        return 2

    new_lines, changed = rewrite_ledger(lines, items, drifted)
    if changed:
        with open(LEDGER, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print("[rewrite] 已回写 %d 项漂移状态到台账。" % len(drifted))
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
