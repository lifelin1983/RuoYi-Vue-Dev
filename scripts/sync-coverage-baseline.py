#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Nightly 回写覆盖率基线 (measured_baseline)。

精确镜像 pom.xml jacoco:check 的 includes 口径（与 ci.yml 的 Aggregate 脚本、徽章
聚合逻辑保持一致），从 jacoco.csv 实算四个门禁目标的实际覆盖率，回写
.harness/enforcement.yml 的 measured_baseline 段（观测事实），门槛阈值
coverage_targets 保持人工维护（策略归人，观测归机器）。

口径（与 pom.xml 严格一致）：
  - CLASS  com.ruoyi.biz.service.impl.*   -> service.impl 整包 LINE%
  - CLASS  com.ruoyi.biz.domain.*         -> domain 整包 + 逐类 LINE%
  - BUNDLE com.ruoyi.biz.*                -> biz bundle LINE%
  - BUNDLE common.utils 精确集合          -> common.utils 子集 LINE%

common.utils 子集（精确枚举，与 pom includes 同口径）：
  顶级类：Arith / StringUtils / DateUtils / sql.SqlUtil
  子包顶级类（JaCoCo '*' 仅匹配前缀后无点的顶级类）：
    uuid.* / sign.* / html.*
  （Base64、Md5Utils、SignUtils 等若落在 sign. 子包下则计入；
    IdUtils/Seq 等顶级类不在列举内则不计入）

退出码：0 始终表示成功（是否提交由 CI step 依 changed 标记决定）。
"""
import csv
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENF = os.path.join(ROOT, ".harness", "enforcement.yml")

# ---- 精确镜像 pom.xml jacoco includes（与 ci.yml Aggregate 脚本同口径）----
COMMON_EXACT = [
    "com.ruoyi.common.utils.Arith",
    "com.ruoyi.common.utils.StringUtils",
    "com.ruoyi.common.utils.DateUtils",
    "com.ruoyi.common.utils.sql.SqlUtil",
]
COMMON_PREFIX = [
    "com.ruoyi.common.utils.uuid.",
    "com.ruoyi.common.utils.sign.",
    "com.ruoyi.common.utils.html.",
]


def module_of(path):
    p = path.replace("\\", "/")
    if "/ruoyi-common/" in p:
        return "ruoyi-common"
    if "/ruoyi-system/" in p:
        return "ruoyi-system"
    return None


def in_common_subset(fqn):
    """精确镜像 pom：4 个顶级类 + uuid./sign./html. 三个子包的顶级类。"""
    if fqn in COMMON_EXACT:
        return True
    for pref in COMMON_PREFIX:
        if fqn.startswith(pref) and "." not in fqn[len(pref):]:
            return True
    return False


def load_rows():
    rows = []
    files = glob.glob(os.path.join(ROOT, "**", "target", "site", "jacoco", "jacoco.csv"),
                      recursive=True)
    if not files:
        files = glob.glob(os.path.join(ROOT, "**", "jacoco.csv"), recursive=True)
    for f in files:
        mod = module_of(f)
        if mod is None:
            continue
        with open(f, newline="", encoding="utf-8") as fh:
            r = csv.reader(fh)
            header = next(r)
            P = header.index("PACKAGE")
            C = header.index("CLASS")
            I = dict((k, i) for i, k in enumerate(header))
            for row in r:
                fqn = row[P] + "." + row[C]
                if mod == "ruoyi-common" and not in_common_subset(fqn):
                    continue
                if mod == "ruoyi-system" and not (
                    fqn.startswith("com.ruoyi.biz.service.impl.")
                    or fqn.startswith("com.ruoyi.biz.domain.")
                ):
                    continue
                lc = int(row[I["LINE_COVERED"]])
                lm = int(row[I["LINE_MISSED"]])
                if lc + lm == 0:
                    continue  # 未加载类不计入，与 JaCoCo BUNDLE 语义一致
                rows.append({"fqn": fqn, "pkg": row[P], "cls": row[C],
                             "lc": lc, "lm": lm})
    return rows


def pct(covered, missed):
    total = covered + missed
    return (covered / total * 100.0 if total else 0.0), total


def fmt(p):
    return "%.2f%%" % p


def main():
    rows = load_rows()
    if not rows:
        print("[sync-baseline] ERROR: no jacoco.csv found under", ROOT)
        return 2

    impl = [x for x in rows if x["pkg"] == "com.ruoyi.biz.service.impl"]
    dom = [x for x in rows if x["pkg"] == "com.ruoyi.biz.domain"]
    biz = [x for x in rows if x["pkg"].startswith("com.ruoyi.biz")]
    common = [x for x in rows if x["pkg"].startswith("com.ruoyi.common.utils")]

    impl_lc, impl_lm = sum(x["lc"] for x in impl), sum(x["lm"] for x in impl)
    dom_lc, dom_lm = sum(x["lc"] for x in dom), sum(x["lm"] for x in dom)
    biz_lc, biz_lm = sum(x["lc"] for x in biz), sum(x["lm"] for x in biz)
    com_lc, com_lm = sum(x["lc"] for x in common), sum(x["lm"] for x in common)
    impl_p, _ = pct(impl_lc, impl_lm)
    dom_p, _ = pct(dom_lc, dom_lm)
    biz_p, _ = pct(biz_lc, biz_lm)
    com_p, _ = pct(com_lc, com_lm)
    impl_total, dom_total, biz_total = impl_lc + impl_lm, dom_lc + dom_lm, biz_lc + biz_lm

    dom_classes = sorted(dom, key=lambda x: x["cls"])
    dom_detail = " / ".join(
        "%s %s (%d/%d 行)" % (c["cls"], fmt(pct(c["lc"], c["lm"])[0]),
                              c["lc"], c["lc"] + c["lm"])
        for c in dom_classes
    )
    com_classes = sorted(common, key=lambda x: x["fqn"])
    com_detail = " / ".join(
        "%s %s" % (c["cls"], fmt(pct(c["lc"], c["lm"])[0]))
        for c in com_classes
    )

    new_values = {
        "com.ruoyi.biz.service.impl": "%s (SysProduct/SysStudent)" % fmt(impl_p),
        "com.ruoyi.biz.domain": "整包 %s；逐类 %s" % (fmt(dom_p), dom_detail),
        "com.ruoyi.biz_bundle": "%s (%d/%d 行)" % (fmt(biz_p), biz_lc, biz_total),
        "com.ruoyi.common.utils": "%s (%d 个已加载类精确集合：%s)" % (
            fmt(com_p), len(com_classes), com_detail),
    }

    with open(ENF, encoding="utf-8") as fh:
        lines = fh.readlines()

    changed = False
    pat = re.compile(
        r'^(\s*)(com\.ruoyi\.(?:biz\.service\.impl|biz\.domain|biz_bundle|common\.utils)):\s*".*"$')
    out = []
    for line in lines:
        m = pat.match(line.rstrip("\n"))
        if m and m.group(2) in new_values:
            indent, key = m.group(1), m.group(2)
            new_line = '%s%s: "%s"\n' % (indent, key, new_values[key])
            if new_line != line:
                changed = True
            out.append(new_line)
        else:
            out.append(line)

    with open(ENF, "w", encoding="utf-8") as fh:
        fh.writelines(out)

    print("[sync-baseline] service.impl =", new_values["com.ruoyi.biz.service.impl"])
    print("[sync-baseline] domain       =", new_values["com.ruoyi.biz.domain"])
    print("[sync-baseline] biz_bundle   =", new_values["com.ruoyi.biz_bundle"])
    print("[sync-baseline] common.utils =", new_values["com.ruoyi.common.utils"])
    print("[sync-baseline] common 计入类 (%d):" % len(com_classes),
          ", ".join(c["fqn"] for c in com_classes))
    print("[sync-baseline] changed =", changed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
