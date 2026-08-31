#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# knowledge-freshness - documentation drift gate
#
# Harness Engineering, Pillar 3 (Entropy Management / Garbage Collection).
#
# Docs are the single source of truth for the AI agent. When a doc drifts,
# the agent acts on a lie. This script turns "docs must not drift" from a
# wish into a deterministic, machine-executable check.
#
# Checks
#   1. Paths declared in .claude/CLAUDE.md exist
#   2. Paths declared in .harness/enforcement.yml (docs: / test_classes:) exist
#   3. Every relative markdown link under docs/, .claude/ and root *.md resolves
#   4. Declared total_tests matches actual test count in source
#      NOTE: ArchUnit rules are declared as @ArchTest FIELDS, not @Test
#      methods. Counting @Test alone undercounts by 11 and produces a false
#      positive that looks exactly like real drift. Both must be counted.
#   5. Every method referenced by enforcement.yml pending_defects exists
#   6. No doc still claims "zero tests" while tests actually exist
#
# Usage
#   bash scripts/check-doc-links.sh            # errors fail (exit 1)
#   bash scripts/check-doc-links.sh --strict   # errors AND warnings fail
#
# Exit code
#   0 = clean   1 = drift detected
# ---------------------------------------------------------------------------

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

# Requires bash 4+ for associative arrays (Git Bash / ubuntu both qualify).
declare -A _seen_err
declare -A _seen_warn

errors=0
warnings=0
checks=0

# Dedupe by key: the same missing path is often found by more than one checker
# (path scan vs markdown link scan). Report it once.
fail() {
    local key="$1" msg="$2"
    [ -n "${_seen_err["$key"]:-}" ] && return
    _seen_err["$key"]=1
    printf 'ERROR    %s\n' "$msg"
    errors=$((errors + 1))
}
warn() {
    local key="$1" msg="$2"
    [ -n "${_seen_warn["$key"]:-}" ] && return
    _seen_warn["$key"]=1
    printf 'WARN     %s\n' "$msg"
    warnings=$((warnings + 1))
}
pass() { checks=$((checks + 1)); }

exists_or_fail() {
    local target="$1" origin="$2"
    if [ -e "$target" ]; then pass; else
        fail "$target" "missing path: $target   <- referenced by $origin"
    fi
}

# Fallback resolution: file-relative first, then repo-root-relative.
# Agent instruction files (CLAUDE.md) commonly write repo-root-relative links
# such as "docs/architecture/overview.md". Resolving those file-relative
# yields ".claude/docs/architecture/overview.md" and reports a false positive.
# Only fail when BOTH resolutions miss - that is a genuinely broken link.
resolve_md_link() {
    local dir="$1" link="$2" origin="$3"
    if [ -e "$dir/$link" ]; then pass; return; fi
    if [ -e "$link" ]; then pass; return; fi
    fail "$link" "broken link: $link   <- referenced by $origin   (tried $dir/$link and $link)"
}

# Java test sources: exclude build output and frontend.
test_sources() {
    find . \( -path ./ruoyi-ui -o -path '*/target/*' -o -path '*/node_modules/*' \) -prune \
         -o -path '*/src/test/java/*' -name '*.java' -type f -print 2>/dev/null | sort
}

md_files() {
    { find docs .claude -type f -name '*.md' 2>/dev/null
      find . -maxdepth 1 -type f -name '*.md' 2>/dev/null; } | sort -u
}

echo "== knowledge-freshness :: documentation drift check =="
echo "   repo root : $ROOT"
echo "   mode      : $([ "$STRICT" -eq 1 ] && echo strict || echo normal)"
echo

# ---------------------------------------------------------------------------
# 1. Paths referenced by .claude/CLAUDE.md
# ---------------------------------------------------------------------------
echo "-- [1/6] CLAUDE.md referenced paths"
if [ -f .claude/CLAUDE.md ]; then
    while IFS= read -r p; do
        [ -n "$p" ] && exists_or_fail "$p" ".claude/CLAUDE.md"
    done < <(grep -oE '(docs|\.claude|\.harness)/[A-Za-z0-9._/-]+\.(md|ya?ml)' \
             .claude/CLAUDE.md 2>/dev/null | sort -u)
else
    fail ".claude/CLAUDE.md" "missing path: .claude/CLAUDE.md   <- required agent entry point"
fi

# ---------------------------------------------------------------------------
# 2. Paths declared in .harness/enforcement.yml
# ---------------------------------------------------------------------------
echo "-- [2/6] enforcement.yml declared paths"
if [ -f .harness/enforcement.yml ]; then
    while IFS= read -r p; do
        [ -n "$p" ] && exists_or_fail "$p" ".harness/enforcement.yml [docs]"
    done < <(awk '
        /^docs:/                              { inb = 1; next }
        inb && /^[A-Za-z_]+:/                 { inb = 0 }
        inb && /^[[:space:]]+[A-Za-z_]+:[[:space:]]+/ {
            sub(/^[[:space:]]+[A-Za-z_]+:[[:space:]]+/, "")
            gsub(/[[:space:]]+$/, ""); print
        }' .harness/enforcement.yml)

    while IFS= read -r p; do
        [ -n "$p" ] && exists_or_fail "$p" ".harness/enforcement.yml [test_classes]"
    done < <(awk '
        /^[[:space:]]*test_classes:/          { inb = 1; next }
        inb && /^[[:space:]]*-[[:space:]]+/ {
            sub(/^[[:space:]]*-[[:space:]]+/, "")
            gsub(/[[:space:]]+$/, ""); print; next
        }
        inb && !/^[[:space:]]*-/              { inb = 0 }
    ' .harness/enforcement.yml | sort -u)
else
    fail ".harness/enforcement.yml" "missing path: .harness/enforcement.yml   <- machine-readable constraint source"
fi

# ---------------------------------------------------------------------------
# 3. Relative markdown links
# ---------------------------------------------------------------------------
echo "-- [3/6] markdown relative links"
while IFS= read -r f; do
    dir="$(dirname "$f")"
    while IFS= read -r link; do
        case "$link" in
            http://*|https://*|mailto:*|'#'*) continue ;;
        esac
        target="${link%%#*}"
        case "$target" in
            ''|http://*|https://*) continue ;;
        esac
        resolve_md_link "$dir" "$target" "$f"
    done < <(grep -oE '\]\([^)]+\)' "$f" 2>/dev/null \
             | sed -E 's/^\]\(//; s/\)$//' | sort -u)
done < <(md_files)

# ---------------------------------------------------------------------------
# 4. Declared test count vs actual
# ---------------------------------------------------------------------------
echo "-- [4/6] test count consistency"
actual_tests=0
while IFS= read -r jf; do
    n=$(grep -cE '^[[:space:]]*@(Test|ArchTest)' "$jf" 2>/dev/null)
    n="${n:-0}"
    actual_tests=$((actual_tests + n))
done < <(test_sources)

declared_tests=""
if [ -f .harness/enforcement.yml ]; then
    declared_tests=$(grep -oE 'total_tests:[[:space:]]*[0-9]+' .harness/enforcement.yml \
                     | head -1 | grep -oE '[0-9]+')
fi

if [ -n "$declared_tests" ]; then
    if [ "$actual_tests" -ne "$declared_tests" ]; then
        warn "count-drift" "test count drift: enforcement.yml total_tests=$declared_tests, source has $actual_tests"
    else
        pass
    fi
else
    warn "no-total-tests" "enforcement.yml has no total_tests declaration - count drift unverifiable"
fi

# ---------------------------------------------------------------------------
# 5. pending_defects referenced methods still exist
# ---------------------------------------------------------------------------
echo "-- [5/6] pending_defects method references"
if [ -f .harness/enforcement.yml ]; then
    while IFS= read -r ref; do
        [ -z "$ref" ] && continue
        cls="${ref%%.*}"
        mth="${ref#*.}"
        [ "$cls" = "$ref" ] && continue
        jf=$(test_sources | grep -E "/${cls}\.java$" | head -1)
        if [ -z "$jf" ]; then
            warn "pd-class:$cls" "pending_defects references unknown class: $cls (from $ref)"
        elif grep -q "$mth" "$jf"; then
            pass
        else
            warn "pd-method:$ref" "pending_defects method not found: $ref   (searched $jf)"
        fi
    done < <(grep -oE '^[[:space:]]*test:[[:space:]]*[A-Za-z0-9_]+\.[A-Za-z0-9_]+' \
             .harness/enforcement.yml 2>/dev/null \
             | sed -E 's/^[[:space:]]*test:[[:space:]]*//' | sort -u)
fi

# ---------------------------------------------------------------------------
# 6. Contradiction: docs claiming zero tests while tests exist
# ---------------------------------------------------------------------------
# Patterns stay narrow on purpose. A broad "docs must be perfect" rule drowns
# the signal in noise; a narrow rule that fires on real contradictions gets
# acted on. Legitimate historical baselines are allowlisted explicitly below
# rather than silently swallowed.
echo "-- [6/6] stale-claim contradiction check"
STALE_PATTERN='零测试|零自动化测试|无.*src/test'

# Sections that describe a historical baseline rather than current state.
# A changelog line saying "fixed the zero-test claim" is not itself a
# zero-test claim, and the sprint background table legitimately records the
# 2026-08-30 starting point. Exempt by SECTION, not by line number - line
# numbers shift the moment anyone edits the file. Scope is bounded by heading
# level: the exemption ends at the next heading of the same or higher level,
# so it cannot silently swallow the rest of the document.
if [ "$actual_tests" -gt 0 ]; then
    while IFS= read -r f; do
        while IFS= read -r hit; do
            [ -z "$hit" ] && continue
            warn "stale:$hit" "stale claim: $hit asserts zero tests, but source has $actual_tests tests"
        done < <(awk -v pat="$STALE_PATTERN" '
            {
                if ($0 ~ /^#+[[:space:]]/) {
                    match($0, /^#+/); hl = RLENGTH
                    if ($0 ~ /(变更记录|变更历史|迭代背景)/) { hist = 1; hlvl = hl }
                    else if (hist && hl <= hlvl) { hist = 0 }
                    next
                }
                if (hist) next
                if ($0 ~ pat) print FILENAME ":" FNR
            }' "$f" 2>/dev/null)
    done < <(md_files)
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo
echo "=================== SUMMARY ==================="
echo "checks passed : $checks"
echo "errors        : $errors"
echo "warnings      : $warnings"
echo "==============================================="

if [ "$errors" -gt 0 ]; then
    echo "RESULT: FAIL - broken reference(s) detected"
    exit 1
fi
if [ "$STRICT" -eq 1 ] && [ "$warnings" -gt 0 ]; then
    echo "RESULT: FAIL - strict mode, warnings present"
    exit 1
fi
if [ "$warnings" -gt 0 ]; then
    echo "RESULT: PASS with warnings"
    exit 0
fi
echo "RESULT: PASS"
exit 0
