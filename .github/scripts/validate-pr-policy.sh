#!/usr/bin/env bash
set -euo pipefail

title="${1:-}"
head_ref="${2:-}"
head_repository="${3:-}"
base_repository="${4:-}"
base_ref="${5:-}"

title_pattern='^(feat|fix|docs|refactor|test|build|ci|chore|perf|revert)(\([a-z0-9][a-z0-9._/-]*\))?!?: .+'
branch_pattern='^(feature|fix|docs|refactor|test|build|ci|chore|release|dependabot)/[A-Za-z0-9._/-]+$'
semver_core='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
release_title_pattern="^chore\\(release\\): release ${semver_core}$"

if [[ -z "${head_ref}" || -z "${head_repository}" || -z "${base_repository}" || -z "${base_ref}" ]]; then
  echo "Pull request branch, repository, and destination metadata are required."
  exit 1
fi

case "${base_ref}" in
  dev)
    if [[ ! "${title}" =~ ${title_pattern} ]]; then
      echo "Pull request title must use an approved Conventional Commit type."
      echo "Current title: ${title}"
      exit 1
    fi

    if [[ "${head_repository}" == "${base_repository}" && ! "${head_ref}" =~ ${branch_pattern} ]]; then
      echo "Repository-owned branches must use an approved temporary prefix."
      echo "Current source branch: ${head_ref}"
      exit 1
    fi

    echo "Accepted pull request title: ${title}"
    if [[ "${head_repository}" == "${base_repository}" ]]; then
      echo "Accepted repository-owned source branch: ${head_ref}"
    else
      echo "Accepted external fork source branch: ${head_repository}:${head_ref}"
    fi
    ;;
  main)
    if [[ "${head_repository}" != "${base_repository}" ]]; then
      echo "Release pull requests must originate from this repository."
      exit 1
    fi

    if [[ "${head_ref}" != "dev" ]]; then
      echo "Release pull requests must originate from the exact dev branch."
      echo "Current source branch: ${head_ref}"
      exit 1
    fi

    if [[ ! "${title}" =~ ${release_title_pattern} ]]; then
      echo "Release pull request title must match chore(release): release X.Y.Z."
      echo "Current title: ${title}"
      exit 1
    fi

    echo "Accepted release pull request: ${title}"
    ;;
  *)
    echo "Pull requests may target only dev or main."
    echo "Current destination branch: ${base_ref}"
    exit 1
    ;;
esac
