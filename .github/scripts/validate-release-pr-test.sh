#!/usr/bin/env bash
set -euo pipefail

validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-release-pr.sh"
test_root="$(mktemp -d)"
valid_changelog="${test_root}/CHANGELOG.md"
missing_heading_changelog="${test_root}/missing-heading.md"

cleanup() {
  rm -rf "${test_root}"
}
trap cleanup EXIT

cat > "${valid_changelog}" <<'EOF'
# Changelog

## [0.2.0] - 2026-07-30
EOF

cat > "${missing_heading_changelog}" <<'EOF'
# Changelog

## [Unreleased]
EOF

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

expect_pass "matching stable release metadata" \
  "${validator}" "chore(release): release 0.2.0" "0.2.0" "${valid_changelog}"
expect_fail "title and Maven version mismatch" \
  "${validator}" "chore(release): release 0.2.0" "0.2.1" "${valid_changelog}"
expect_fail "snapshot Maven version" \
  "${validator}" "chore(release): release 0.2.0" "0.2.0-SNAPSHOT" "${valid_changelog}"
expect_fail "missing dated changelog heading" \
  "${validator}" "chore(release): release 0.2.0" "0.2.0" "${missing_heading_changelog}"
expect_fail "malformed release title" \
  "${validator}" "chore(release): prepare 0.2.0" "0.2.0" "${valid_changelog}"

echo "All release pull request metadata tests passed."
