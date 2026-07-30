#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
validator="${VALIDATOR_UNDER_TEST:-${script_dir}/validate-pr-policy.sh}"
workflow="${WORKFLOW_UNDER_TEST:-${script_dir}/../workflows/ci.yml}"
repository="EmmanuelCazarez/criteriaforge"
bootstrap_base_sha="cab27f008b664df78ac83247f3ad27cf160fa72e"
wrong_base_sha="0000000000000000000000000000000000000000"
normal_release_base_sha="1111111111111111111111111111111111111111"

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

assert_workflow_conditions() {
  ruby -ryaml - "${workflow}" <<'RUBY'
workflow = YAML.load_file(ARGV.fetch(0))
steps = workflow.fetch("jobs").fetch("pr-policy").fetch("steps")
setup = steps.find { |step| step["name"] == "Set up Java 17 for release metadata validation" }
metadata = steps.find { |step| step["name"] == "Validate release pull request metadata" }
expected = "github.base_ref == 'main' && github.event.pull_request.title != 'ci: adopt release-only main governance'"

unless setup&.fetch("if", nil) == expected && metadata&.fetch("if", nil) == expected
  warn "FAIL: release metadata steps must skip only the exact governance title."
  exit 1
end

metadata_runs = lambda do |base_ref, title|
  base_ref == "main" && title != "ci: adopt release-only main governance"
end

if metadata_runs.call("main", "ci: adopt release-only main governance")
  warn "FAIL: exact governance title should skip release metadata."
  exit 1
end
unless metadata_runs.call("main", "chore(release): release 0.2.0")
  warn "FAIL: normal main release title should run release metadata."
  exit 1
end
if metadata_runs.call("dev", "chore(release): release 0.2.0")
  warn "FAIL: dev-targeting pull requests should not run release metadata."
  exit 1
end
RUBY
}

expect_pass "feature branch and feat title" \
  "${validator}" "feat(jpa): add grouped projections" \
  "feature/grouped-projections" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "release branch and release title" \
  "${validator}" "chore(release): prepare 0.2.0" \
  "release/0.2.0" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "breaking conventional title" \
  "${validator}" "feat(core)!: replace the parser contract" \
  "feature/parser-v2" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "docs branch and docs title" \
  "${validator}" "docs: clarify setup" \
  "docs/setup" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "fix branch and fix title" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "fix/repeated-parameters" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "refactor branch and refactor title" \
  "${validator}" "refactor(core): simplify parser" \
  "refactor/parser" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "test branch and test title" \
  "${validator}" "test(jpa): cover grouped projections" \
  "test/grouped-projections" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "build branch and build title" \
  "${validator}" "build: refresh plugins" \
  "build/plugins" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "ci branch and ci title" \
  "${validator}" "ci: update checks" \
  "ci/checks" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "chore branch and chore title" \
  "${validator}" "chore: update metadata" \
  "chore/metadata" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "dependabot branch and chore title" \
  "${validator}" "chore(deps): bump actions/checkout" \
  "dependabot/github_actions/actions-checkout-7" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_pass "fork branch is allowed for dev" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "my-personal-branch" "contributor/criteriaforge" "${repository}" "dev" "${bootstrap_base_sha}"

expect_pass "normal release remains valid after bootstrap base expires" \
  "${validator}" "chore(release): release 0.2.0" \
  "dev" "${repository}" "${repository}" "main" "${normal_release_base_sha}"
expect_pass "exact one-time governance bootstrap to main" \
  "${validator}" "ci: adopt release-only main governance" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "governance bootstrap rejects a different base SHA" \
  "${validator}" "ci: adopt release-only main governance" \
  "dev" "${repository}" "${repository}" "main" "${wrong_base_sha}"
expect_fail "governance bootstrap rejects a wrong head ref" \
  "${validator}" "ci: adopt release-only main governance" \
  "ci/governance" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "governance bootstrap rejects a wrong head repository" \
  "${validator}" "ci: adopt release-only main governance" \
  "dev" "contributor/criteriaforge" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "governance bootstrap rejects a wrong base repository" \
  "${validator}" "ci: adopt release-only main governance" \
  "dev" "${repository}" "OtherOwner/criteriaforge" "main" "${bootstrap_base_sha}"
expect_fail "governance bootstrap rejects a wrong base ref" \
  "${validator}" "ci: adopt release-only main governance" \
  "dev" "${repository}" "${repository}" "staging" "${bootstrap_base_sha}"
expect_fail "governance bootstrap rejects a near-match title" \
  "${validator}" "ci: adopt release only main governance" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "governance bootstrap rejects a missing base SHA" \
  "${validator}" "ci: adopt release-only main governance" \
  "dev" "${repository}" "${repository}" "main"
expect_fail "invalid pull request title" \
  "${validator}" "Add grouped projections" \
  "feature/grouped-projections" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_fail "same-repository branch without an approved prefix" \
  "${validator}" "fix(web): preserve repeated parameters" \
  "work/repeated-parameters" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_fail "missing title" \
  "${validator}" "" "feature/example" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_fail "empty branch suffix" \
  "${validator}" "docs: clarify setup" "docs/" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_fail "feature branch cannot target main" \
  "${validator}" "feat: add feature" \
  "feature/add-feature" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "main branch cannot target main" \
  "${validator}" "chore: maintain main" \
  "main" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "release branch cannot target main" \
  "${validator}" "chore(release): release 0.2.0" \
  "release/0.2.0" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "fork dev cannot target main" \
  "${validator}" "chore(release): release 0.2.0" \
  "dev" "contributor/criteriaforge" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "dev to main requires release title" \
  "${validator}" "chore(release): prepare 0.2.0" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "release title rejects snapshot" \
  "${validator}" "chore(release): release 0.2.0-SNAPSHOT" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "release title requires patch number" \
  "${validator}" "chore(release): release 0.2" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "release title rejects leading-zero numeric parts" \
  "${validator}" "chore(release): release 01.2.0" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "release title rejects extra text" \
  "${validator}" "chore(release): release 0.2.0 now" \
  "dev" "${repository}" "${repository}" "main" "${bootstrap_base_sha}"
expect_fail "unsupported source prefix targeting dev" \
  "${validator}" "feat: bypass policy" \
  "hotfix/bypass" "${repository}" "${repository}" "dev" "${bootstrap_base_sha}"
expect_fail "unsupported base branch" \
  "${validator}" "feat: add feature" \
  "feature/add-feature" "${repository}" "${repository}" "staging" "${bootstrap_base_sha}"

assert_workflow_conditions

echo "All pull request policy tests passed."
