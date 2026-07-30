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
  "feature/grouped-projections" "${repository}" "${repository}" "dev"
expect_pass "release branch and release title" \
  "${validator}" "chore(release): prepare 0.2.0" \
  "release/0.2.0" "${repository}" "${repository}" "dev"
expect_pass "breaking conventional title" \
  "${validator}" "feat(core)!: replace the parser contract" \
  "feature/parser-v2" "${repository}" "${repository}" "dev"
expect_pass "docs branch and docs title" \
  "${validator}" "docs: clarify setup" \
  "docs/setup" "${repository}" "${repository}" "dev"
expect_pass "fix branch and fix title" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "fix/repeated-parameters" "${repository}" "${repository}" "dev"
expect_pass "refactor branch and refactor title" \
  "${validator}" "refactor(core): simplify parser" \
  "refactor/parser" "${repository}" "${repository}" "dev"
expect_pass "test branch and test title" \
  "${validator}" "test(jpa): cover grouped projections" \
  "test/grouped-projections" "${repository}" "${repository}" "dev"
expect_pass "build branch and build title" \
  "${validator}" "build: refresh plugins" \
  "build/plugins" "${repository}" "${repository}" "dev"
expect_pass "ci branch and ci title" \
  "${validator}" "ci: update checks" \
  "ci/checks" "${repository}" "${repository}" "dev"
expect_pass "chore branch and chore title" \
  "${validator}" "chore: update metadata" \
  "chore/metadata" "${repository}" "${repository}" "dev"
expect_pass "dependabot branch and chore title" \
  "${validator}" "chore(deps): bump actions/checkout" \
  "dependabot/github_actions/actions-checkout-7" "${repository}" "${repository}" "dev"
expect_pass "fork branch is allowed for dev" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "my-personal-branch" "contributor/criteriaforge" "${repository}" "dev"

expect_pass "dev promotes an exact release to main" \
  "${validator}" "chore(release): release 0.2.0" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "invalid pull request title" \
  "${validator}" "Add grouped projections" \
  "feature/grouped-projections" "${repository}" "${repository}" "dev"
expect_fail "same-repository branch without an approved prefix" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "work/repeated-parameters" "${repository}" "${repository}" "dev"
expect_fail "missing title" \
  "${validator}" "" "feature/example" "${repository}" "${repository}" "dev"
expect_fail "empty branch suffix" \
  "${validator}" "docs: clarify setup" "docs/" "${repository}" "${repository}" "dev"
expect_fail "feature branch cannot target main" \
  "${validator}" "feat: add feature" \
  "feature/add-feature" "${repository}" "${repository}" "main"
expect_fail "main branch cannot target main" \
  "${validator}" "chore: maintain main" \
  "main" "${repository}" "${repository}" "main"
expect_fail "release branch cannot target main" \
  "${validator}" "chore(release): release 0.2.0" \
  "release/0.2.0" "${repository}" "${repository}" "main"
expect_fail "fork dev cannot target main" \
  "${validator}" "chore(release): release 0.2.0" \
  "dev" "contributor/criteriaforge" "${repository}" "main"
expect_fail "dev to main requires release title" \
  "${validator}" "chore(release): prepare 0.2.0" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "release title rejects snapshot" \
  "${validator}" "chore(release): release 0.2.0-SNAPSHOT" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "release title requires patch number" \
  "${validator}" "chore(release): release 0.2" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "release title rejects leading-zero numeric parts" \
  "${validator}" "chore(release): release 01.2.0" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "release title rejects extra text" \
  "${validator}" "chore(release): release 0.2.0 now" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "unsupported source prefix targeting dev" \
  "${validator}" "feat: bypass policy" \
  "hotfix/bypass" "${repository}" "${repository}" "dev"
expect_fail "unsupported base branch" \
  "${validator}" "feat: add feature" \
  "feature/add-feature" "${repository}" "${repository}" "staging"

echo "All pull request policy tests passed."
