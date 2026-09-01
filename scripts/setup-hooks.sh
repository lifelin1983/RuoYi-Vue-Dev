#!/usr/bin/env bash
# 一次性启用本仓库的 pre-commit 钩子（Harness Engineering）
# 等价命令：git config core.hooksPath scripts/git-hooks
set -uo pipefail

git config core.hooksPath scripts/git-hooks
echo "pre-commit hook enabled: core.hooksPath = $(git config core.hooksPath)"
echo "提示："
echo "  - 每次 git commit 会先跑 scripts/check-doc-links.sh --strict"
echo "  - SKIP_DOC_CHECK=1 git commit ...  临时跳过文档校验"
echo "  - git commit --no-verify          完全绕过本机钩子（不推荐，CI 仍会拦）"
