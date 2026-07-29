#!/usr/bin/env bash
set -euo pipefail
tag="${1:-}"
expected_fingerprint="${2:-}"
public_key="${3:-}"

if [[ ! "${tag}" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Release tag must use vX.Y.Z without leading zeroes."
  exit 1
fi

if [[ ! "${expected_fingerprint}" =~ ^[A-F0-9]{40}$ ]]; then
  echo "Expected signing fingerprint must contain 40 uppercase hexadecimal characters."
  exit 1
fi

if [[ ! -f "${public_key}" ]] || grep -q "PRIVATE KEY" "${public_key}"; then
  echo "A public-only armored signing key is required."
  exit 1
fi

if ! git show-ref --verify --quiet "refs/tags/${tag}"; then
  echo "Release tag does not exist: ${tag}"
  exit 1
fi

if [[ "$(git cat-file -t "refs/tags/${tag}")" != "tag" ]]; then
  echo "Release tag must be annotated: ${tag}"
  exit 1
fi

verification_home="$(mktemp -d)"
chmod 700 "${verification_home}"
cleanup() {
  GNUPGHOME="${verification_home}" gpgconf --kill all >/dev/null 2>&1 || true
  rm -rf "${verification_home}"
}
trap cleanup EXIT

GNUPGHOME="${verification_home}" gpg --batch --import "${public_key}" >/dev/null 2>&1
if ! verification_output="$(GNUPGHOME="${verification_home}" git verify-tag --raw "${tag}" 2>&1)"; then
  echo "Release tag signature is invalid: ${tag}"
  exit 1
fi

actual_fingerprint="$(
  printf '%s\n' "${verification_output}" |
    awk '$1 == "[GNUPG:]" && $2 == "VALIDSIG" { print $3; exit }'
)"
if [[ "${actual_fingerprint}" != "${expected_fingerprint}" ]]; then
  echo "Release tag was not signed by the pinned maintainer key."
  exit 1
fi

if ! git show-ref --verify --quiet refs/remotes/origin/main; then
  echo "origin/main must be fetched before candidate validation."
  exit 1
fi

commit="$(git rev-list -n 1 "refs/tags/${tag}")"
if ! git merge-base --is-ancestor "${commit}" origin/main; then
  echo "Release tag commit must be reachable from origin/main."
  exit 1
fi

if [[ "$(git rev-parse HEAD)" != "${commit}" ]]; then
  echo "The working tree must be checked out at the exact tagged commit."
  exit 1
fi

version="${tag#v}"
project_version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
if [[ "${project_version}" != "${version}" || "${project_version}" == *-SNAPSHOT ]]; then
  echo "Maven project version must equal the non-snapshot tag version."
  exit 1
fi

printf 'tag=%s\nversion=%s\ncommit=%s\n' "${tag}" "${version}" "${commit}"
