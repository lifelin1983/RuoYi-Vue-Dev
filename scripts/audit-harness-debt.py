#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Harness 债务台账自动巡检（零依赖：仅用标准库，GitHub Actions 直接 python3 可跑）。

功能：
  1. 解析 .harness/harness-debt.yaml（轻量行解析，无需 pyyaml）
  2. 对 verifiable:true 的项实跑 check：
       - type: test_exists          (Level 0) 校验「测试类 + 方法」是否仍存在于 src/test 源码
       - type: coverage_delta       (Level 1) 校验指定包当前 LINE% 是否跌破声明红线
       - type: new_untested_class   (Level 1) 校验指定包下新增类是否都有对应测试类
  3. 两类语义不同：
       - 验证型（test_exists）：仅当声明 status ∈ {resolved, mitigated} 时有意义；
         此时若 check 证据失效 → 漂移（记 drift）。
       - 监控型（coverage_delta / new_untested_class）：持续有效，check FAIL 即记 drift；
         若 status 已是 drift 且 check 恢复 PASS → 自动撤销 drift（回到 open）。
         无 jacoco.csv（未构建）时 coverage_delta 降级为 SKIP，不误报漂移。
  4. 漂移时（--rewrite）回写台账：status → drift，并写入 drift_note；
     恢复时（--rewrite）撤销：status → open，并删除 drift_note。
  5. 退出码：0=健康（无漂移/恢复）/ 2=有漂移或恢复并已回写 / 1=脚本错误

用法：
  python3 scripts/audit-harness-debt.py            # 仅报告，不改文件
  python3 scripts/audit-harness-debt.py --rewrite  # 漂移/恢复则回写台账并 exit 2
"""
import os
import re
import sys
import csv
import glob
from datetime import date

# ---- 路径定位：脚本位于 <root>/scripts/，台账位于 <root>/.harness/harness-debt.yaml ----
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
LEDGER = os.path.join(PROJECT_ROOT, ".harness", "harness-debt.yaml")

# 监控型 check：持续有效，FAIL 即记 drift（无论声明 status）
MONITOR_TYPES = ("coverage_delta", "new_untested_class")


def _float(s, default):
    try:
        return float(str(s).strip().rstrip("%"))
    except (ValueError, AttributeError):
        return default


def parse_ledger(path):
    """轻量行解析 ledger，返回 (lines, items)。item 含 _line 起始行号。"""
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    items = []
    cur = None
    in_check = False
    for i, line in enumerate(lines):
        m = re.match(r"^  - id:\s*(\S+)", line)
        if m:
            cur = {"id": m.group(1), "_line": i}
            items.append(cur)
            in_check = False
            continue
        if cur is None:
            continue
        v = line.rstrip("\n")
        m = re.match(r"^    status:\s*(\S+)", v)
        if m:
            cur["status"] = m.group(1)
            in_check = False
            continue
        m = re.match(r"^    verifiable:\s*(\S+)", v)
        if m:
            cur["verifiable"] = m.group(1).lower() == "true"
            in_check = False
            continue
        m = re.match(r"^    kind:\s*(\S+)", v)
        if m:
            cur["kind"] = m.group(1)
            in_check = False
            continue
        m = re.match(r"^    priority:\s*(\S+)", v)
        if m:
            cur["priority"] = m.group(1)
            in_check = False
            continue
        m = re.match(r"^    title:\s*(.+)", v)
        if m:
            cur.setdefault("title", m.group(1).strip())
            in_check = False
            continue
        m = re.match(r"^    check:", v)
        if m:
            cur.setdefault("check", {})
            in_check = True
            continue
        # check 块内的子字段（6 空格）
        m = re.match(r"^      (\S+):\s*(.+)", v)
        if m and in_check and cur.get("check") is not None:
            # 剥离行内注释（如 "type: test_exists   # Level 0..."）
            cur["check"][m.group(1)] = m.group(2).strip().split("#")[0].strip()
            continue
        # 任意 4 空格或更浅字段出现 → 结束 check 块
        if re.match(r"^    \S", v):
            in_check = False
    return lines, items


def test_exists(test_ref):
    """test_ref = 'ClassName.methodName'，校验类与方法是否仍存在于 src/test 源码。"""
    if "." not in test_ref:
        return False, None
    class_name, method_name = test_ref.rsplit(".", 1)
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


def load_package_line_coverage(pkg_prefix):
    """从 jacoco.csv 实算指定包前缀的整包 LINE%（百分比数值）。
    找不到 jacoco.csv 或该包无数据 → 返回 None（调用方应 SKIP）。
    口径：PACKAGE 点分隔；只统计已加载类（covered+missed>0）。"""
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
                C = h.index("CLASS")
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
    """源码类 short 是否有对应测试类（宽松命名变体，避免误报未测）。"""
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
                test_names.add(fn[:-5])   # 去掉 .java，保留完整类名（含 Test 后缀）
            elif fn.endswith(".java") and "Test" in fn:
                test_names.add(fn[:-5])
    return [s for s in src_classes if not class_has_test(s, test_names)]


def run_check(item):
    """返回 (ok: bool, detail: str)。ok 为 True 不代表健康——监控型 SKIP 也返回 True。"""
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
    if ctype == "coverage_delta":
        pkg = check.get("package", "")
        baseline = _float(check.get("baseline", "0"), 0.0)
        tol = _float(check.get("tolerance", "0"), 0.0)
        cur = load_package_line_coverage(pkg)
        if cur is None:
            return True, "coverage_delta SKIP: 无 jacoco.csv（未构建），不记漂移"
        if cur >= baseline - tol:
            return True, "coverage_delta PASS: %s 当前 %.2f%% ≥ 红线 %.2f%% (tol %.2f)" % (
                pkg, cur, baseline, tol)
        return False, "coverage_delta FAIL: %s 当前 %.2f%% < 红线 %.2f%% (tol %.2f)" % (
            pkg, cur, baseline, tol)
    if ctype == "new_untested_class":
        pkg = check.get("package", "")
        un = find_untested_classes(pkg)
        if not un:
            return True, "new_untested_class PASS: %s 下所有类均有对应测试类" % pkg
        return False, "new_untested_class FAIL: %s 下未测类: %s" % (pkg, ", ".join(un))
    return True, "check type '%s' 未实现，跳过" % ctype


def audit(lines, items, rewrite):
    report = []
    drifted = []
    recovered = []
    for it in items:
        vid = it.get("id", "?")
        title = it.get("title", "")
        status = it.get("status", "open")
        verb = it.get("verifiable", False)
        ctype = (it.get("check") or {}).get("type")
        line = ["[%s] %s  (status=%s, verifiable=%s, check=%s)" % (vid, title, status, verb, ctype)]
        if not verb:
            line.append("    → 人工维护项（verifiable:false），仅记录，不自动校验")
            report.append("\n".join(line))
            continue
        ok, detail = run_check(it)
        skipped = "SKIP" in detail.upper()
        if ctype in MONITOR_TYPES:
            if skipped:
                line.append("    → %s" % detail)
            elif not ok:
                line.append("    → %s" % detail)
                drifted.append(it)
            elif status == "drift":
                line.append("    → %s · 监控恢复，撤销 drift" % detail)
                recovered.append(it)
            else:
                line.append("    → %s" % detail)
        else:
            # 验证型（test_exists 等）
            if status in ("resolved", "mitigated"):
                if not ok:
                    line.append("    → %s · 证据失效，记 drift" % detail)
                    drifted.append(it)
                else:
                    line.append("    → %s" % detail)
            else:
                line.append("    → 验证型(status=%s)仅记录: %s" % (status, detail))
        report.append("\n".join(line))
    return report, drifted, recovered


def rewrite_ledger(lines, items, drifted, recovered):
    """对漂移项回写 status=drift + drift_note；对恢复项撤销 drift、删 drift_note。"""
    if not drifted and not recovered:
        return lines, False
    drift_ids = {d["id"] for d in drifted}
    rec_ids = {r["id"] for r in recovered}
    bounds = {}
    for idx, it in enumerate(items):
        start = it["_line"]
        end = items[idx + 1]["_line"] if idx + 1 < len(items) else len(lines)
        bounds[it["id"]] = (start, end)

    today = date.today().isoformat()
    out = list(lines)
    changed = False

    for d in drifted:
        sid = d["id"]
        start, end = bounds[sid]
        for j in range(start, end):
            if re.match(r"^    status:\s*", out[j]):
                out[j] = "    status: drift\n"
                changed = True
                has_note = any(re.match(r"^    drift_note:", out[k]) for k in range(start, end))
                if not has_note:
                    ev = (d.get("check") or {}).get("package",
                                                    (d.get("check") or {}).get("test", ""))
                    note = ('    drift_note: "%s · nightly 巡检：%s 触发漂移，'
                            '需人工复核（%s）"\n' % (today, (d.get("check") or {}).get("type", ""), ev))
                    out.insert(j + 1, note)
                    for other in items:
                        if other["_line"] > j:
                            other["_line"] += 1
                break

    for r in recovered:
        sid = r["id"]
        start, end = bounds[sid]
        for j in range(start, end):
            if re.match(r"^    status:\s*drift\s*$", out[j]):
                out[j] = "    status: open\n"
                changed = True
                for k in range(start, min(end + 50, len(out))):
                    if re.match(r"^    drift_note:", out[k]):
                        del out[k]
                        for other in items:
                            if other["_line"] > k:
                                other["_line"] -= 1
                        break
                break

    return out, changed


def main():
    args = sys.argv[1:]
    rewrite = "--rewrite" in args
    if not os.path.exists(LEDGER):
        sys.stderr.write("ERROR: ledger not found: %s\n" % LEDGER)
        return 1
    lines, items = parse_ledger(LEDGER)
    report, drifted, recovered = audit(lines, items, rewrite)

    print("=" * 72)
    print("Harness 债务台账巡检  ·  %s" % LEDGER)
    print("=" * 72)
    for block in report:
        print(block)
    print("-" * 72)
    print("总计：%d 项，漂移 %d 项，恢复 %d 项" % (len(items), len(drifted), len(recovered)))
    for d in drifted:
        print("  ⚠ 漂移: %s (%s)" % (d["id"], (d.get("check") or {}).get("type", "")))
    for r in recovered:
        print("  ✓ 恢复: %s" % r["id"])
    print("=" * 72)

    if not drifted and not recovered:
        return 0

    if not rewrite:
        print("[report-only] 发现漂移/恢复但未指定 --rewrite，不回写台账。")
        return 2

    new_lines, changed = rewrite_ledger(lines, items, drifted, recovered)
    if changed:
        with open(LEDGER, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print("[rewrite] 已回写 %d 项漂移 / %d 项恢复到台账。" % (len(drifted), len(recovered)))
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
