#!/usr/bin/env bash
set -euo pipefail

validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-release-candidate.sh"
public_key="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/release-signing-key.asc"
release_workflow="$(cd "$(dirname "${BASH_SOURCE[0]}")/../workflows" && pwd)/release.yml"
fingerprint="27CE82768E118D3F80256BB6E60E5A6A6709E150"
repository_root="$(git rev-parse --show-toplevel)"
test_root="$(cd "$(mktemp -d)" && pwd -P)"
candidate_worktree="${test_root}/v0.1.0"

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

cleanup() {
  local cleanup_status=0
  local command_status

  if git -C "${repository_root}" worktree list --porcelain |
    grep -Fx "worktree ${candidate_worktree}" >/dev/null; then
    git -C "${repository_root}" worktree remove --force "${candidate_worktree}" ||
      cleanup_status="$?"
  fi
  rm -rf "${test_root}" || {
    command_status="$?"
    if [[ "${cleanup_status}" -eq 0 ]]; then
      cleanup_status="${command_status}"
    fi
  }

  return "${cleanup_status}"
}
trap cleanup EXIT

assert_release_workflow_contract() {
  ruby -ryaml -rjson - "${release_workflow}" <<'RUBY'
workflow = YAML.load_file(ARGV.fetch(0))
release_expression = %q!${{ github.event_name == 'workflow_dispatch' && format('v{0}', inputs.version) || github.ref_name }}!
errors = []

concurrency_group = workflow.dig("concurrency", "group")
release_tag = workflow.dig("env", "RELEASE_TAG")
unless concurrency_group == "release-#{release_expression}" && release_tag == release_expression
  errors << "FAIL: release tag expression must be event-derived and shared by concurrency and RELEASE_TAG."
end

candidate_steps = workflow.dig("jobs", "candidate", "steps") || []
checkouts = candidate_steps.select { |step| step["uses"] == "actions/checkout@v7" }
trusted_checkout = checkouts.find { |step| step.dig("with", "path") == "trusted-tools" }
candidate_checkout = checkouts.find { |step| step.dig("with", "path") == "candidate" }
unless trusted_checkout&.dig("with", "ref") == "main" &&
    trusted_checkout&.dig("with", "fetch-depth") == 0 &&
    candidate_checkout&.dig("with", "ref") == "${{ env.RELEASE_TAG }}" &&
    candidate_checkout&.dig("with", "fetch-depth") == 0
  errors << "FAIL: candidate job must check out trusted main tooling separately from the immutable candidate."
end

fetch_step = candidate_steps.find { |step| step["name"] == "Fetch main without rewriting tags" }
validation_step = candidate_steps.find { |step| step["name"] == "Validate signed release candidate" }
validation_run = validation_step&.fetch("run", "") || ""
unless fetch_step&.fetch("run", "") == "git -C candidate fetch --no-tags origin main:refs/remotes/origin/main" &&
    validation_run.include?("cd candidate") &&
    validation_run.include?("../trusted-tools/.github/scripts/validate-release-candidate.sh") &&
    validation_run.include?("../trusted-tools/.github/release-signing-key.asc")
  errors << "FAIL: trusted validator and public key must validate the separate candidate checkout."
end

unless errors.empty?
  warn errors.join("\n")
  exit 1
end
RUBY
}

assert_cleanup_contract() {
  local probe_root="${test_root}/cleanup-probe"
  local cleanup_log="${probe_root}/cleanup.log"
  local cleanup_status
  local failed=0
  mkdir -p "${probe_root}/tree"

  set +e
  (
    set -euo pipefail
    test_root="${probe_root}/tree"
    candidate_worktree="${test_root}/candidate"

    git() {
      if [[ "$*" == *"worktree list --porcelain"* ]]; then
        printf 'worktree %s\n' "${candidate_worktree}"
        return 0
      fi
      if [[ "$*" == *"worktree remove --force"* ]]; then
        printf 'worktree-remove\n' >> "${cleanup_log}"
        return 23
      fi
      return 99
    }

    rm() {
      printf 'rm\n' >> "${cleanup_log}"
      return 0
    }

    trap cleanup EXIT
    exit 0
  )
  cleanup_status="$?"
  set -e

  if [[ "${cleanup_status}" -eq 0 ]]; then
    echo "FAIL: cleanup should retain a nonzero worktree-removal result."
    failed=1
  fi
  if ! grep -Fxq "worktree-remove" "${cleanup_log}"; then
    echo "FAIL: cleanup should attempt worktree removal."
    failed=1
  fi
  if ! grep -Fxq "rm" "${cleanup_log}"; then
    echo "FAIL: cleanup should attempt temporary directory removal after worktree removal fails."
    failed=1
  fi

  return "${failed}"
}

regression_failures=0
if ! assert_release_workflow_contract; then
  regression_failures=1
fi
if ! assert_cleanup_contract; then
  regression_failures=1
fi
if [[ "${regression_failures}" -ne 0 ]]; then
  exit 1
fi

git -C "${repository_root}" worktree add --detach "${candidate_worktree}" v0.1.0
if [[ -e "${candidate_worktree}/.github/scripts/validate-release-candidate.sh" ||
  -e "${candidate_worktree}/.github/release-signing-key.asc" ]]; then
  echo "FAIL: the historical candidate should not provide trusted release tooling."
  exit 1
fi
(
  cd "${candidate_worktree}"
  expect_fail "malformed tag" \
    "${validator}" "release-0.1.0" "${fingerprint}" "${public_key}"
  expect_fail "missing annotated tag" \
    "${validator}" "v999.999.999" "${fingerprint}" "${public_key}"
  expect_fail "wrong signing fingerprint" \
    "${validator}" "v0.1.0" "0000000000000000000000000000000000000000" "${public_key}"
  expect_pass "published v0.1.0 candidate" \
    "${validator}" "v0.1.0" "${fingerprint}" "${public_key}"
)

echo "All release candidate tests passed."
