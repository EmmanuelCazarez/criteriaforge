#!/usr/bin/env bash
set -euo pipefail

title="${1:-}"
project_version="${2:-}"
changelog_file="${3:-}"

semver_core='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
release_title_pattern="^chore\\(release\\): release (${semver_core})$"

if [[ ! "${title}" =~ ${release_title_pattern} ]]; then
  echo "Release pull request title must match chore(release): release X.Y.Z."
  exit 1
fi

release_version="${BASH_REMATCH[1]}"

if [[ ! "${project_version}" =~ ^${semver_core}$ ]]; then
  echo "Maven project version must be a stable X.Y.Z release version."
  exit 1
fi

if [[ "${project_version}" != "${release_version}" ]]; then
  echo "Maven project version must exactly match the release pull request title."
  exit 1
fi

if [[ ! -f "${changelog_file}" ]]; then
  echo "Release changelog file is required."
  exit 1
fi

if ! grep -Eq "^## \\[${release_version}\\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" "${changelog_file}"; then
  echo "CHANGELOG.md must contain a dated heading for the release version."
  exit 1
fi

echo "Accepted release metadata for version ${release_version}."
