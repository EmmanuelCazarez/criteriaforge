#!/usr/bin/env bash
set -euo pipefail

validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-pr-policy.sh"
repository="EmmanuelCazarez/criteriaforge"

expect_pass() {
  local label="$1"
  shift
  if ! "$@"; then
    echo "FAIL: ${label} should pass."
    exit 1
  fi
}

expect_fail() {
  local label="$1"
  shift
  if "$@"; then
    echo "FAIL: ${label} should fail."
    exit 1
  fi
}

expect_pass "feature branch and feat title" \
  "${validator}" "feat(jpa): add grouped projections" \
  "feature/grouped-projections" "${repository}" "${repository}"
expect_pass "release branch and release title" \
  "${validator}" "chore(release): prepare 0.2.0" \
  "release/0.2.0" "${repository}" "${repository}"
expect_pass "breaking conventional title" \
  "${validator}" "feat(core)!: replace the parser contract" \
  "feature/parser-v2" "${repository}" "${repository}"
expect_pass "fork branch is not name-restricted" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "my-personal-branch" "contributor/criteriaforge" "${repository}"

expect_fail "invalid pull request title" \
  "${validator}" "Add grouped projections" \
  "feature/grouped-projections" "${repository}" "${repository}"
expect_fail "same-repository branch without an approved prefix" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "work/repeated-parameters" "${repository}" "${repository}"
expect_fail "missing title" \
  "${validator}" "" "feature/example" "${repository}" "${repository}"
expect_fail "empty branch suffix" \
  "${validator}" "docs: clarify setup" "docs/" "${repository}" "${repository}"

echo "All pull request policy tests passed."
