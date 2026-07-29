#!/usr/bin/env bash
set -euo pipefail

validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-release-candidate.sh"
public_key="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/release-signing-key.asc"
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
  if git -C "${repository_root}" worktree list --porcelain |
    grep -Fx "worktree ${candidate_worktree}" >/dev/null; then
    git -C "${repository_root}" worktree remove --force "${candidate_worktree}"
  fi
  rm -rf "${test_root}"
}
trap cleanup EXIT

git -C "${repository_root}" worktree add --detach "${candidate_worktree}" v0.1.0
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
