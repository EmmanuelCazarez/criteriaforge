# CriteriaForge Trunk, Release, and Repository Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `main` the only permanent CriteriaForge branch, enforce one squash commit per pull request, protect release tags, and publish Maven Central candidates only from verified signed tags after the complete compatibility matrix succeeds.

**Architecture:** Repository-owned shell validators provide locally testable pull-request and release-candidate policy. GitHub Actions composes those validators with the Java 17, Spring Boot 3 and 4, PostgreSQL, quality, dependency-review, and CodeQL jobs. Version-controlled repository settings and ruleset JSON are applied only after a migration pull request proves every required check name, preventing a protection lockout.

**Tech Stack:** Bash, Git, GnuPG, GitHub Actions, GitHub REST API through `gh`, Maven Wrapper, Java 17, Spring Boot 3.5.16 and 4.1.0, PostgreSQL Testcontainers, Maven Central Publisher Portal.

## Global Constraints

- Preserve the signed `v0.1.0` tag and all published `0.1.0` Maven coordinates unchanged.
- Do not rewrite existing `main` history; the one-commit policy applies to pull requests merged after migration.
- Use `main` as the default and only permanent branch.
- Merge pull requests with squash only; the squash title is the pull request title and the body is the pull request description.
- Automatically delete merged same-repository source branches; fork branches remain under the contributor's control.
- Permit repository-owned branches only under `feature/`, `fix/`, `docs/`, `refactor/`, `test/`, `build/`, `ci/`, `chore/`, `release/`, or `dependabot/`.
- Accept any source-branch name from a fork.
- Require Conventional Commit pull request titles using `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`, or `revert`.
- Require Java 17 verification against Spring Boot 3.5.16 and 4.1.0, PostgreSQL integration tests, quality checks, dependency review, and Java CodeQL.
- Keep Spring Boot 3 as the dependency baseline while testing Spring Boot 4 as a compatibility target.
- Keep `criteriaforge-example` in GitHub source and release tags but exclude it from Maven Central publication.
- Keep `autoPublish=false`; a maintainer publishes a validated deployment manually in Central.
- Keep Maven Central credentials and the private signing key only in the protected `maven-central` environment.
- Never print, commit, or copy credential values into logs, pull requests, issues, release notes, or plan output.
- Keep automatic Dependabot security pull requests and all automatic merging disabled.
- Do not delete `origin/feature/criteriaforge-library` until commit `cfca670` is accepted or rejected in the dedicated public-API/code task.
- Use `apply_patch` for repository file edits and explicitly stage only the files owned by each task.

## File Responsibility Map

- `.github/scripts/validate-pr-policy.sh`: validates the permanent squash commit title and same-repository head-branch prefix.
- `.github/scripts/validate-pr-policy-test.sh`: executable local contract tests for pull-request policy.
- `.github/scripts/validate-release-candidate.sh`: verifies signed annotated release tags, signer fingerprint, `main` ancestry, and Maven version.
- `.github/scripts/validate-release-candidate-test.sh`: exercises malformed, missing, wrong-signer, and valid `v0.1.0` candidates.
- `.github/workflows/ci.yml`: required pull-request and `main` build matrix plus policy and dependency review.
- `.github/workflows/codeql.yml`: Java CodeQL analysis for pull requests, `main`, manual runs, and a weekly schedule.
- `.github/workflows/release.yml`: unprivileged candidate/matrix jobs followed by protected Maven Central upload.
- `.github/release-signing-key.asc`: public release-tag verification key only.
- `.github/dependabot.yml`: weekly Maven and Actions updates targeting `main`, with Spring Boot semantic-major updates ignored.
- `.github/repository-settings.json`: version-controlled desired merge, branch-cleanup, and security settings.
- `.github/rulesets/protect-main.json`: source of truth for active `main` protection.
- `.github/rulesets/restrict-release-tag-creation.json`: allows only repository administrators to create `v*` tags.
- `.github/rulesets/protect-release-tags.json`: blocks updates and deletion of `v*` tags without a normal bypass.
- `pom.xml` and module POMs: carry the intended `0.1.1-SNAPSHOT` development line and reviewed dependency updates from `dev`.
- `docs/branching.md`: contributor-facing trunk workflow.
- `CONTRIBUTING.md`: local checks, branch names, pull-request title rules, and squash behavior.
- `RELEASING.md`: signed-tag release preparation, protected upload, Central publication, and post-release snapshot flow.
- `docs/superpowers/specs/2026-07-29-trunk-release-and-repository-protection-design.md`: approved design record.

---

### Task 1: Create an isolated migration worktree and capture the safety baseline

**Files:**
- Read: `.git`
- Read: `docs/superpowers/specs/2026-07-29-trunk-release-and-repository-protection-design.md`
- Read: `docs/superpowers/plans/2026-07-29-trunk-release-and-repository-protection.md`

**Interfaces:**
- Consumes: remote refs `origin/main`, `origin/dev`, `origin/feature/criteriaforge-library`, and signed tag `v0.1.0`.
- Produces: isolated branch `codex/trunk-release-protection` based exactly on the refreshed `origin/main`.

- [ ] **Step 1: Invoke the worktree safety workflow**

Read and follow `superpowers:using-git-worktrees` before creating the implementation checkout. Keep the current divergent local `dev` checkout intact.

- [ ] **Step 2: Refresh and record remote state**

Run:

```bash
git fetch --prune origin
git status --short --branch
git rev-parse origin/main
git rev-parse origin/dev
git rev-parse origin/feature/criteriaforge-library
git rev-list --left-right --count origin/main...origin/dev
git diff --name-status origin/main..origin/dev
git log --oneline origin/dev..origin/feature/criteriaforge-library
git tag --verify v0.1.0
gh pr list --state open --json number,title,headRefName,baseRefName,url
```

Expected:

- `origin/main` and `origin/dev` resolve.
- `origin/feature/criteriaforge-library` resolves to unique commit `cfca670` unless a later public-API decision has already handled it.
- `v0.1.0` verifies with fingerprint `27CE82768E118D3F80256BB6E60E5A6A6709E150`.
- Any new open pull request is reviewed before migration proceeds.

- [ ] **Step 3: Create the isolated branch from `origin/main`**

Create `codex/trunk-release-protection` from the refreshed `origin/main` in the worktree chosen by the worktree skill:

```bash
git switch --create codex/trunk-release-protection origin/main
```

Expected: `git status --short --branch` reports a clean branch based on `origin/main`.

- [ ] **Step 4: Carry the approved design and plan into the branch**

Locate the commit that owns this plan without assuming the local `dev` tip:

```bash
criteriaforge_plan_commit="$(
  git log -1 --format=%H dev -- \
    docs/superpowers/plans/2026-07-29-trunk-release-and-repository-protection.md
)"
test -n "${criteriaforge_plan_commit}"
git cherry-pick 7f0035b "${criteriaforge_plan_commit}"
```

Expected: the approved design and this plan exist in the isolated branch; no production or workflow file has changed yet.

- [ ] **Step 5: Re-run the unique-work guard**

Run:

```bash
git merge-base --is-ancestor cfca670 HEAD
```

Expected: exit code `1`. This proves the out-of-scope public-API refactor has not been silently imported.

---

### Task 2: Implement the pull-request policy validator with contract tests

**Files:**
- Create: `.github/scripts/validate-pr-policy.sh`
- Create: `.github/scripts/validate-pr-policy-test.sh`

**Interfaces:**
- Consumes: positional arguments `TITLE`, `HEAD_REF`, `HEAD_REPOSITORY`, and `BASE_REPOSITORY`.
- Produces: exit `0` for an accepted pull request and non-zero with a safe diagnostic for a rejected title or same-repository branch.

- [ ] **Step 1: Add the failing executable contract test**

Create `.github/scripts/validate-pr-policy-test.sh`:

```bash
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
```

Make it executable and run:

```bash
chmod +x .github/scripts/validate-pr-policy-test.sh
.github/scripts/validate-pr-policy-test.sh
```

Expected: FAIL because `.github/scripts/validate-pr-policy.sh` does not exist.

- [ ] **Step 2: Add the minimal validator**

Create `.github/scripts/validate-pr-policy.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

title="${1:-}"
head_ref="${2:-}"
head_repository="${3:-}"
base_repository="${4:-}"

title_pattern='^(feat|fix|docs|refactor|test|build|ci|chore|perf|revert)(\([a-z0-9][a-z0-9._/-]*\))?!?: .+'
branch_pattern='^(feature|fix|docs|refactor|test|build|ci|chore|release|dependabot)/[A-Za-z0-9._/-]+$'

if [[ ! "${title}" =~ ${title_pattern} ]]; then
  echo "Pull request title must use an approved Conventional Commit type."
  echo "Current title: ${title}"
  exit 1
fi

if [[ -z "${head_ref}" || -z "${head_repository}" || -z "${base_repository}" ]]; then
  echo "Pull request branch and repository metadata are required."
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
```

Make it executable:

```bash
chmod +x .github/scripts/validate-pr-policy.sh
```

- [ ] **Step 3: Run the validator tests**

Run:

```bash
.github/scripts/validate-pr-policy-test.sh
```

Expected: `All pull request policy tests passed.`

- [ ] **Step 4: Check shell syntax**

Run:

```bash
bash -n .github/scripts/validate-pr-policy.sh
bash -n .github/scripts/validate-pr-policy-test.sh
```

Expected: both commands exit `0` without output.

- [ ] **Step 5: Commit the policy validator**

```bash
git add .github/scripts/validate-pr-policy.sh .github/scripts/validate-pr-policy-test.sh
git commit -m "ci: validate pull request policy"
```

---

### Task 3: Make the complete CI and security matrix target `main`

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `.github/workflows/codeql.yml`

**Interfaces:**
- Consumes: `.github/scripts/validate-pr-policy.sh` from Task 2 and Maven profiles in `pom.xml`.
- Produces: stable required check contexts `pr-policy`, `build-java17-boot3`, `build-java17-boot4`, `postgresql`, `quality`, `dependency-review`, and `codeql-java`.

- [ ] **Step 1: Restrict CI triggers to trunk**

Replace the `on` block in `.github/workflows/ci.yml` with:

```yaml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
  workflow_dispatch:
```

Keep the existing concurrency block. Use `actions/checkout@v7` and `actions/setup-java@v5` in every Java job.

- [ ] **Step 2: Add the pull-request policy job**

Add this first job to `.github/workflows/ci.yml`:

```yaml
  pr-policy:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - name: Validate pull request title and source branch
        env:
          PR_TITLE: ${{ github.event.pull_request.title }}
          HEAD_REF: ${{ github.head_ref }}
          HEAD_REPOSITORY: ${{ github.event.pull_request.head.repo.full_name }}
          BASE_REPOSITORY: ${{ github.repository }}
        run: >-
          .github/scripts/validate-pr-policy.sh
          "${PR_TITLE}"
          "${HEAD_REF}"
          "${HEAD_REPOSITORY}"
          "${BASE_REPOSITORY}"
```

The `if` condition keeps the job absent from non-PR runs while preserving the exact `pr-policy` context on pull requests.

- [ ] **Step 3: Add dependency review**

Expand top-level permissions:

```yaml
permissions:
  contents: read
```

Add:

```yaml
  dependency-review:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v7
      - uses: actions/dependency-review-action@v4
        with:
          fail-on-severity: moderate
```

- [ ] **Step 4: Add advanced Java CodeQL**

Create `.github/workflows/codeql.yml`:

```yaml
name: CodeQL

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
  schedule:
    - cron: '23 6 * * 1'
  workflow_dispatch:

permissions:
  contents: read
  security-events: write

concurrency:
  group: codeql-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  codeql-java:
    name: codeql-java
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin
      - name: Build Java 17 and Spring Boot 3 baseline
        run: ./mvnw -B -ntp -DskipTests package -Dspring-boot.version=3.5.16
      - uses: github/codeql-action/analyze@v4
```

- [ ] **Step 5: Validate local policy and workflow syntax**

Run:

```bash
.github/scripts/validate-pr-policy-test.sh
ruby -e 'require "yaml"; ARGV.each { |path| YAML.parse_file(path); puts "valid #{path}" }' \
  .github/workflows/ci.yml .github/workflows/codeql.yml
git diff --check
```

Expected:

- policy tests pass;
- Ruby prints `valid` for both workflows;
- `git diff --check` exits `0`.

- [ ] **Step 6: Commit CI and CodeQL**

```bash
git add .github/workflows/ci.yml .github/workflows/codeql.yml
git commit -m "ci: protect main with full verification"
```

---

### Task 4: Verify signed release candidates before privileged publication

**Files:**
- Create: `.github/release-signing-key.asc`
- Create: `.github/scripts/validate-release-candidate.sh`
- Create: `.github/scripts/validate-release-candidate-test.sh`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: release tag, public key, pinned fingerprint `27CE82768E118D3F80256BB6E60E5A6A6709E150`, `origin/main`, and Maven project version.
- Produces: candidate outputs `tag`, `version`, and `commit`; publication secrets remain inaccessible until all verification jobs pass.

- [ ] **Step 1: Export and inspect the public release key**

Run:

```bash
gpg --armor --export 27CE82768E118D3F80256BB6E60E5A6A6709E150 > /tmp/criteriaforge-release-signing-key.asc
gpg --show-keys --with-colons /tmp/criteriaforge-release-signing-key.asc
```

Expected: the first `fpr` record is exactly `27CE82768E118D3F80256BB6E60E5A6A6709E150`. Copy the complete armored public block into `.github/release-signing-key.asc` with `apply_patch`. The file must begin with `-----BEGIN PGP PUBLIC KEY BLOCK-----` and contain no private-key block.

- [ ] **Step 2: Add the failing release-candidate contract test**

Create `.github/scripts/validate-release-candidate-test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

validator="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-release-candidate.sh"
public_key="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/release-signing-key.asc"
fingerprint="27CE82768E118D3F80256BB6E60E5A6A6709E150"
repository_root="$(git rev-parse --show-toplevel)"
test_root="$(mktemp -d)"
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
    grep -Fxq "worktree ${candidate_worktree}"; then
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
```

Make it executable and run:

```bash
chmod +x .github/scripts/validate-release-candidate-test.sh
.github/scripts/validate-release-candidate-test.sh
```

Expected: FAIL because `.github/scripts/validate-release-candidate.sh` does not exist.

- [ ] **Step 3: Implement the release-candidate validator**

Create `.github/scripts/validate-release-candidate.sh`:

```bash
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
```

Make it executable:

```bash
chmod +x .github/scripts/validate-release-candidate.sh
```

- [ ] **Step 4: Prove release-candidate behavior against immutable `v0.1.0`**

Run:

```bash
.github/scripts/validate-release-candidate-test.sh
```

Expected:

- malformed and missing tags are rejected;
- a mismatched signing fingerprint is rejected;
- the detached signed `v0.1.0` candidate passes with Maven version `0.1.0`;
- the final line is `All release candidate tests passed.`;
- the temporary worktree is removed by the exit trap.

- [ ] **Step 5: Replace release triggers and separate candidate verification**

Set the top of `.github/workflows/release.yml` to:

```yaml
name: Release

on:
  push:
    tags:
      - 'v*.*.*'
  workflow_dispatch:
    inputs:
      version:
        description: Existing signed release version without the v prefix
        required: true
        type: string

permissions:
  contents: read

concurrency:
  group: release-${{ github.ref_name || inputs.version }}
  cancel-in-progress: false

env:
  RELEASE_SIGNING_FINGERPRINT: 27CE82768E118D3F80256BB6E60E5A6A6709E150
```

Add the unprivileged candidate job:

```yaml
  candidate:
    runs-on: ubuntu-latest
    outputs:
      tag: ${{ steps.validate.outputs.tag }}
      version: ${{ steps.validate.outputs.version }}
      commit: ${{ steps.validate.outputs.commit }}
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
          ref: ${{ github.ref_type == 'tag' && github.ref_name || format('v{0}', inputs.version) }}
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Fetch main without rewriting tags
        run: git fetch --no-tags origin main:refs/remotes/origin/main
      - name: Validate signed release candidate
        id: validate
        env:
          RELEASE_TAG: ${{ github.ref_type == 'tag' && github.ref_name || format('v{0}', inputs.version) }}
        run: >-
          .github/scripts/validate-release-candidate.sh
          "${RELEASE_TAG}"
          "${RELEASE_SIGNING_FINGERPRINT}"
          .github/release-signing-key.asc
          >> "${GITHUB_OUTPUT}"
```

The workflow has no `release: published` trigger. A merge into `main` runs CI but does not publish; pushing a valid signed tag starts the release.

- [ ] **Step 6: Add exact-tag release matrix jobs**

Add four jobs, each with `needs: candidate`, `actions/checkout@v7`, and:

```yaml
ref: ${{ needs.candidate.outputs.commit }}
```

Use these stable job names and commands:

```yaml
  build-java17-boot3:
    needs: candidate
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ needs.candidate.outputs.commit }}
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: ./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16

  build-java17-boot4:
    needs: candidate
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ needs.candidate.outputs.commit }}
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: ./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0

  postgresql:
    needs: candidate
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ needs.candidate.outputs.commit }}
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: ./mvnw -B -ntp -Ppostgresql-tests verify

  quality:
    needs: candidate
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ needs.candidate.outputs.commit }}
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: ./mvnw -B -ntp -Pquality,documentation verify
```

- [ ] **Step 7: Keep all secrets in the final protected publish job**

Define:

```yaml
  publish:
    needs:
      - candidate
      - build-java17-boot3
      - build-java17-boot4
      - postgresql
      - quality
    runs-on: ubuntu-latest
    environment: maven-central
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ needs.candidate.outputs.commit }}
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
          server-id: central
          server-username: CENTRAL_USERNAME
          server-password: CENTRAL_PASSWORD
      - name: Import signing key into an ephemeral keyring
        env:
          GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
        run: |
          signing_home="$(mktemp -d)"
          chmod 700 "${signing_home}"
          echo "GNUPGHOME=${signing_home}" >> "${GITHUB_ENV}"
          printf '%s' "${GPG_PRIVATE_KEY}" | base64 --decode |
            GNUPGHOME="${signing_home}" gpg --batch --import
      - name: Upload signed bundle for manual Central publication
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
        run: >-
          ./mvnw -B -ntp -Prelease -DskipTests
          -DexcludeArtifacts=criteriaforge-example
          deploy
      - name: Remove ephemeral signing material
        if: always()
        run: |
          if [[ -n "${GNUPGHOME:-}" && "${GNUPGHOME}" == /tmp/* ]]; then
            gpgconf --kill all || true
            rm -rf "${GNUPGHOME}"
          fi
```

Do not add a GitHub Release creation step. Publish the GitHub Release only after Central publication is confirmed.

- [ ] **Step 8: Validate release configuration**

Run:

```bash
bash -n .github/scripts/validate-release-candidate.sh
bash -n .github/scripts/validate-release-candidate-test.sh
ruby -e 'require "yaml"; YAML.parse_file(".github/workflows/release.yml"); puts "valid release workflow"'
grep -q "BEGIN PGP PUBLIC KEY BLOCK" .github/release-signing-key.asc
! grep -q "PRIVATE KEY" .github/release-signing-key.asc
git diff --check
```

Expected: all commands exit `0`.

- [ ] **Step 9: Commit signed-candidate release automation**

```bash
git add \
  .github/release-signing-key.asc \
  .github/scripts/validate-release-candidate.sh \
  .github/scripts/validate-release-candidate-test.sh \
  .github/workflows/release.yml
git commit -m "ci: verify signed release candidates"
```

---

### Task 5: Integrate intended `dev` state without importing its merge history

**Files:**
- Modify: `pom.xml`
- Modify: `criteriaforge-core/pom.xml`
- Modify: `criteriaforge-example/pom.xml`
- Modify: `criteriaforge-jpa/pom.xml`
- Modify: `criteriaforge-spring-boot-autoconfigure/pom.xml`
- Modify: `criteriaforge-spring-boot-starter/pom.xml`
- Modify: `criteriaforge-spring-web/pom.xml`
- Modify: `.github/dependabot.yml`

**Interfaces:**
- Consumes: reviewed tree differences from `origin/dev`, plus the Spring Boot major-ignore block retained from `origin/main`.
- Produces: `0.1.1-SNAPSHOT` trunk with reviewed dependency updates and weekly Dependabot pull requests targeting `main`.

- [ ] **Step 1: Import only the intended POM tree from `origin/dev`**

Run:

```bash
git restore --source=origin/dev -- \
  pom.xml \
  criteriaforge-core/pom.xml \
  criteriaforge-example/pom.xml \
  criteriaforge-jpa/pom.xml \
  criteriaforge-spring-boot-autoconfigure/pom.xml \
  criteriaforge-spring-boot-starter/pom.xml \
  criteriaforge-spring-web/pom.xml
```

Expected root POM state:

- version `0.1.1-SNAPSHOT`;
- SCM tag `HEAD`;
- ArchUnit `1.4.2`;
- JaCoCo `0.8.15`;
- compiler `3.15.0`;
- enforcer `3.6.3`;
- failsafe and surefire `3.5.6`;
- source plugin `3.4.0`;
- Central publishing plugin `0.11.0`.

- [ ] **Step 2: Preserve the Spring Boot baseline policy while retargeting Dependabot**

Set `.github/dependabot.yml` to:

```yaml
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    target-branch: main
    open-pull-requests-limit: 5
    ignore:
      # Spring Boot 3 remains the library baseline. Boot 4 compatibility is
      # exercised explicitly by CI instead of changing the baseline automatically.
      - dependency-name: "org.springframework.boot:*"
        update-types:
          - "version-update:semver-major"
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    target-branch: main
    open-pull-requests-limit: 5
```

- [ ] **Step 3: Verify the exact integrated tree**

Run:

```bash
./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version
rg -n "<version>0\\.1\\.1-SNAPSHOT</version>" pom.xml criteriaforge-*/pom.xml
rg -n "target-branch: main|version-update:semver-major" .github/dependabot.yml
git diff --check
```

Expected:

- Maven prints `0.1.1-SNAPSHOT`;
- the parent and every child POM use the snapshot line;
- both Dependabot ecosystems target `main`;
- the Spring Boot semantic-major ignore remains present.

- [ ] **Step 4: Run the baseline and Boot 4 builds**

Run:

```bash
./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
```

Expected: both reactor builds end with `BUILD SUCCESS`.

- [ ] **Step 5: Commit the reviewed development baseline**

```bash
git add \
  .github/dependabot.yml \
  pom.xml \
  criteriaforge-core/pom.xml \
  criteriaforge-example/pom.xml \
  criteriaforge-jpa/pom.xml \
  criteriaforge-spring-boot-autoconfigure/pom.xml \
  criteriaforge-spring-boot-starter/pom.xml \
  criteriaforge-spring-web/pom.xml
git commit -m "build: integrate 0.1.1 snapshot baseline"
```

---

### Task 6: Document trunk contribution and signed-tag release operations

**Files:**
- Modify: `docs/branching.md`
- Modify: `CONTRIBUTING.md`
- Modify: `RELEASING.md`

**Interfaces:**
- Consumes: approved design and exact workflow/check names from Tasks 2 through 5.
- Produces: contributor and maintainer instructions consistent with the enforced repository behavior.

- [ ] **Step 1: Replace the branch model documentation**

Rewrite `docs/branching.md` so it states all of the following exactly:

```text
temporary branch or fork -> pull request -> required checks -> squash merge -> main
```

- `main` is the only permanent and default branch.
- Same-repository branch prefixes are the ten prefixes in Global Constraints.
- External fork branch names are accepted.
- Temporary branches may contain multiple commits; GitHub writes one squash commit to `main`.
- Pull request titles use the approved Conventional Commit types.
- The exact required checks are `pr-policy`, `build-java17-boot3`, `build-java17-boot4`, `postgresql`, `quality`, `dependency-review`, and `codeql-java`.
- Same-repository branches are automatically deleted after merge.
- A merge to `main` does not publish; an approved signed `vX.Y.Z` tag starts release verification.
- No `dev` or `qa` branch exists in the final model.

- [ ] **Step 2: Update contribution instructions**

In `CONTRIBUTING.md`, replace the five development steps with:

1. Branch from current `main` using an approved same-repository prefix, or use any branch name in a fork.
2. Add a failing focused test for behavior changes or fixes.
3. Implement the smallest coherent change.
4. Run focused tests and the complete compatibility checks.
5. Open a pull request targeting `main` with a Conventional Commit title and complete compatibility/security notes.
6. Resolve conversations and update the branch with current `main`.
7. Squash-merge after every required check passes; allow automatic deletion of same-repository source branches.

Retain the existing architecture and public-API guidance. Add `dependency-review` and `codeql-java` to the explanation of required remote checks.

- [ ] **Step 3: Replace obsolete first-release instructions**

Rewrite the operational portion of `RELEASING.md` around this exact sequence:

1. Choose `0.1.1` for fixes, `0.2.0` for backward-compatible features, `0.MINOR.0` plus migration notes for breaking `0.x` changes, and `1.0.0` when the public API becomes stable.
2. Create `release/X.Y.Z` from current `main`.
3. Change every reactor version from the current snapshot to `X.Y.Z`, set SCM tag to `vX.Y.Z`, and update `CHANGELOG.md`.
4. Run Boot 3, Boot 4, PostgreSQL, quality/documentation, and release-profile verification.
5. Open `chore(release): prepare X.Y.Z` targeting `main`.
6. Squash-merge after all checks pass and wait for the post-merge `main` CI run.
7. Create annotated signed tag `vX.Y.Z` on the exact successful `main` commit and verify it locally.
8. Push only that tag; the Release workflow verifies the signature and full matrix before requesting `maven-central` approval.
9. Approve the environment, inspect the Central deployment at `VALIDATED`, and publish it manually.
10. Verify the public coordinates, then publish the GitHub Release from the unchanged signed tag.
11. Open and squash a temporary `chore/next-snapshot` pull request that advances to the next planned snapshot.

Retain credential setup and recovery guidance, but remove references to `dev`, publishing a GitHub Release as a workflow trigger, and the already completed “first 0.1.0” preparation.

- [ ] **Step 4: Search for contradictory branch and trigger guidance**

Run:

```bash
rg -n '\bdev\b|release:\s*$|types: \[published\]|feature/\* ->.*dev' \
  README.md CONTRIBUTING.md RELEASING.md docs .github/workflows .github/dependabot.yml
```

Expected: matches appear only in the historical approved design, this implementation plan, or migration explanations that explicitly say `dev` is removed. No active contributor or workflow instruction targets `dev`.

- [ ] **Step 5: Validate documentation formatting**

Run:

```bash
git diff --check
```

Expected: exit `0`.

- [ ] **Step 6: Commit trunk and release documentation**

```bash
git add docs/branching.md CONTRIBUTING.md RELEASING.md
git commit -m "docs: adopt trunk and signed-tag releases"
```

---

### Task 7: Version-control repository settings and protection rules

**Files:**
- Create: `.github/repository-settings.json`
- Create: `.github/rulesets/protect-main.json`
- Create: `.github/rulesets/restrict-release-tag-creation.json`
- Create: `.github/rulesets/protect-release-tags.json`

**Interfaces:**
- Consumes: stable check contexts produced by Tasks 2 and 3.
- Produces: idempotent JSON request bodies for repository settings and three active GitHub rulesets.

- [ ] **Step 1: Add desired repository settings**

Create `.github/repository-settings.json`:

```json
{
  "allow_merge_commit": false,
  "allow_squash_merge": true,
  "allow_rebase_merge": false,
  "allow_auto_merge": false,
  "delete_branch_on_merge": true,
  "squash_merge_commit_title": "PR_TITLE",
  "squash_merge_commit_message": "PR_BODY",
  "security_and_analysis": {
    "secret_scanning": {
      "status": "enabled"
    },
    "secret_scanning_push_protection": {
      "status": "enabled"
    }
  }
}
```

- [ ] **Step 2: Add the active `main` ruleset definition**

Create `.github/rulesets/protect-main.json`:

```json
{
  "name": "protect-main",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": {
    "ref_name": {
      "include": [
        "refs/heads/main"
      ],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "deletion"
    },
    {
      "type": "non_fast_forward"
    },
    {
      "type": "required_linear_history"
    },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": [
          "squash"
        ]
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "do_not_enforce_on_create": false,
        "strict_required_status_checks_policy": true,
        "required_status_checks": [
          {
            "context": "pr-policy"
          },
          {
            "context": "build-java17-boot3"
          },
          {
            "context": "build-java17-boot4"
          },
          {
            "context": "postgresql"
          },
          {
            "context": "quality"
          },
          {
            "context": "dependency-review"
          },
          {
            "context": "codeql-java"
          }
        ]
      }
    }
  ]
}
```

This intentionally has no normal bypass actor and does not require signed commits on `main`.

- [ ] **Step 3: Add administrator-only release-tag creation**

Create `.github/rulesets/restrict-release-tag-creation.json`:

```json
{
  "name": "restrict-release-tag-creation",
  "target": "tag",
  "enforcement": "active",
  "bypass_actors": [
    {
      "actor_id": 5,
      "actor_type": "RepositoryRole",
      "bypass_mode": "always"
    }
  ],
  "conditions": {
    "ref_name": {
      "include": [
        "refs/tags/v*"
      ],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "creation"
    }
  ]
}
```

Repository role ID `5` is the administrator role. This bypass applies only to
the creation ruleset, allowing administrators to create a signed release tag
without weakening immutability.

- [ ] **Step 4: Add release-tag immutability without a normal bypass**

Create `.github/rulesets/protect-release-tags.json`:

```json
{
  "name": "protect-release-tags",
  "target": "tag",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": {
    "ref_name": {
      "include": [
        "refs/tags/v*"
      ],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "update"
    },
    {
      "type": "deletion"
    }
  ]
}
```

An unpublished invalid tag can be deleted only by an explicit temporary
administrative change to this ruleset, followed by immediate restoration. A
published tag is never moved or deleted.

- [ ] **Step 5: Validate all JSON and rule invariants**

Run:

```bash
jq empty \
  .github/repository-settings.json \
  .github/rulesets/protect-main.json \
  .github/rulesets/restrict-release-tag-creation.json \
  .github/rulesets/protect-release-tags.json
jq -e '.allow_squash_merge and (.allow_merge_commit | not) and (.allow_rebase_merge | not) and .delete_branch_on_merge' \
  .github/repository-settings.json
jq -e '.rules[] | select(.type == "required_status_checks") |
  .parameters.required_status_checks | length == 7' \
  .github/rulesets/protect-main.json
jq -e '.target == "tag" and
  ([.rules[].type] | sort) == (["creation"] | sort) and
  (.bypass_actors | length) == 1' \
  .github/rulesets/restrict-release-tag-creation.json
jq -e '.target == "tag" and
  ([.rules[].type] | sort) == (["deletion", "update"] | sort) and
  (.bypass_actors | length) == 0' \
  .github/rulesets/protect-release-tags.json
git diff --check
```

Expected: every command exits `0`.

- [ ] **Step 6: Commit version-controlled administration**

```bash
git add \
  .github/repository-settings.json \
  .github/rulesets/protect-main.json \
  .github/rulesets/restrict-release-tag-creation.json \
  .github/rulesets/protect-release-tags.json
git commit -m "ci: define repository protection rules"
```

---

### Task 8: Run the complete local gate and open the migration pull request

**Files:**
- Verify: all files changed by Tasks 1 through 7

**Interfaces:**
- Consumes: isolated implementation branch and all local validators/workflows.
- Produces: one migration pull request targeting `main`; eventual squash merge writes exactly one logical commit to `main`.

- [ ] **Step 1: Run local policy and syntax tests**

Run:

```bash
.github/scripts/validate-pr-policy-test.sh
bash -n .github/scripts/validate-release-candidate.sh
bash -n .github/scripts/validate-release-candidate-test.sh
ruby -e 'require "yaml"; ARGV.each { |path| YAML.parse_file(path); puts "valid #{path}" }' \
  .github/workflows/ci.yml \
  .github/workflows/codeql.yml \
  .github/workflows/release.yml \
  .github/dependabot.yml
jq empty .github/repository-settings.json .github/rulesets/*.json
git diff --check
```

Expected: all tests and parsers pass.

- [ ] **Step 2: Run the complete Java matrix**

Run:

```bash
./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
./mvnw -B -ntp -Ppostgresql-tests verify
./mvnw -B -ntp -Pquality,documentation verify
./mvnw -B -ntp -Prelease -Dgpg.skip=true -Dcentral.skipPublishing=true clean verify
```

Expected: all five commands end with `BUILD SUCCESS`. The release-profile command creates source and Javadoc artifacts without signing or contacting Central.

- [ ] **Step 3: Verify migration scope**

Run:

```bash
git status --short --branch
git diff --name-status origin/main...HEAD
git log --oneline origin/main..HEAD
git merge-base --is-ancestor cfca670 HEAD
```

Expected:

- only files named in this plan are changed;
- the implementation branch has reviewable task commits;
- the final command exits `1`, proving unique public-API work remains outside this migration.

- [ ] **Step 4: Push the migration branch**

```bash
git push --set-upstream origin codex/trunk-release-protection
```

Expected: push succeeds and no tag is pushed.

- [ ] **Step 5: Open the migration pull request**

Create a ready pull request targeting `main`:

```text
Title: ci: adopt trunk and signed-tag releases

Body:
## Summary
- make main the only permanent integration branch
- require squash-oriented CI, dependency review, and CodeQL
- verify signed release tags before protected Maven Central upload
- retain the 0.1.1-SNAPSHOT dependency updates without importing dev merge history

## Verification
- Java 17 / Spring Boot 3.5.16
- Java 17 / Spring Boot 4.1.0
- PostgreSQL Testcontainers
- quality and documentation profiles
- release profile with signing and Central publication disabled
- shell, YAML, and JSON policy validation

## Safety
- v0.1.0 and published coordinates are unchanged
- criteriaforge-example remains excluded from Maven Central
- feature/criteriaforge-library is preserved for a separate public-API decision
```

- [ ] **Step 6: Verify exact remote check names**

Run:

```bash
gh pr checks --watch
gh pr view --json statusCheckRollup --jq \
  '.statusCheckRollup[] | [.name, .status, .conclusion] | @tsv'
```

Expected successful contexts include exactly:

```text
pr-policy
build-java17-boot3
build-java17-boot4
postgresql
quality
dependency-review
codeql-java
```

If GitHub reports a different context, correct the workflow job `name` and the matching `.github/rulesets/protect-main.json` context in the same pull request, rerun this step, and proceed only when all seven exact names succeed.

- [ ] **Step 7: Squash-merge the migration pull request**

Confirm the PR title still passes the policy, then squash-merge using the PR title and body:

```bash
gh pr merge --squash --delete-branch
```

Expected:

- `main` gains exactly one commit for the migration PR;
- `codex/trunk-release-protection` is deleted remotely;
- the post-merge `main` CI and CodeQL runs complete successfully.

---

### Task 9: Apply repository settings and rulesets after checks exist

**Files:**
- Read: `.github/repository-settings.json`
- Read: `.github/rulesets/protect-main.json`
- Read: `.github/rulesets/restrict-release-tag-creation.json`
- Read: `.github/rulesets/protect-release-tags.json`

**Interfaces:**
- Consumes: merged source-of-truth JSON and successful check contexts on `main`.
- Produces: squash-only merge settings, branch deletion, secret protection, active `protect-main`, administrator-only release-tag creation, and active release-tag immutability.

- [ ] **Step 1: Refresh the merged source of truth**

Run:

```bash
git fetch --prune origin
git switch main
git pull --ff-only origin main
jq empty .github/repository-settings.json .github/rulesets/*.json
```

Expected: local `main` equals `origin/main`.

- [ ] **Step 2: Apply repository merge and security settings**

Run:

```bash
gh api --method PATCH \
  repos/EmmanuelCazarez/criteriaforge \
  --input .github/repository-settings.json
gh api --method PUT \
  repos/EmmanuelCazarez/criteriaforge/vulnerability-alerts
gh api --method DELETE \
  repos/EmmanuelCazarez/criteriaforge/automated-security-fixes
```

Expected:

- squash merge enabled;
- merge commits and rebase merge disabled;
- auto-merge disabled;
- source-branch deletion enabled;
- secret scanning and push protection enabled;
- vulnerability alerts enabled;
- automatic Dependabot security pull requests disabled.

- [ ] **Step 3: Create or update `protect-main` idempotently**

Run:

```bash
criteriaforge_main_ruleset_id="$(
  gh api repos/EmmanuelCazarez/criteriaforge/rulesets \
    --jq '.[] | select(.name == "protect-main") | .id'
)"
if [[ -n "${criteriaforge_main_ruleset_id}" ]]; then
  gh api --method PUT \
    "repos/EmmanuelCazarez/criteriaforge/rulesets/${criteriaforge_main_ruleset_id}" \
    --input .github/rulesets/protect-main.json
else
  gh api --method POST \
    repos/EmmanuelCazarez/criteriaforge/rulesets \
    --input .github/rulesets/protect-main.json
fi
```

Expected: one active `protect-main` ruleset exists and targets only `refs/heads/main`.

- [ ] **Step 4: Create or update the release-tag creation ruleset idempotently**

Run:

```bash
criteriaforge_tag_creation_ruleset_id="$(
  gh api repos/EmmanuelCazarez/criteriaforge/rulesets \
    --jq '.[] | select(.name == "restrict-release-tag-creation") | .id'
)"
if [[ -n "${criteriaforge_tag_creation_ruleset_id}" ]]; then
  gh api --method PUT \
    "repos/EmmanuelCazarez/criteriaforge/rulesets/${criteriaforge_tag_creation_ruleset_id}" \
    --input .github/rulesets/restrict-release-tag-creation.json
else
  gh api --method POST \
    repos/EmmanuelCazarez/criteriaforge/rulesets \
    --input .github/rulesets/restrict-release-tag-creation.json
fi
```

Expected: one active `restrict-release-tag-creation` ruleset exists and
permits repository administrators to create `v*` tags.

- [ ] **Step 5: Create or update release-tag immutability idempotently**

Run:

```bash
criteriaforge_tag_ruleset_id="$(
  gh api repos/EmmanuelCazarez/criteriaforge/rulesets \
    --jq '.[] | select(.name == "protect-release-tags") | .id'
)"
if [[ -n "${criteriaforge_tag_ruleset_id}" ]]; then
  gh api --method PUT \
    "repos/EmmanuelCazarez/criteriaforge/rulesets/${criteriaforge_tag_ruleset_id}" \
    --input .github/rulesets/protect-release-tags.json
else
  gh api --method POST \
    repos/EmmanuelCazarez/criteriaforge/rulesets \
    --input .github/rulesets/protect-release-tags.json
fi
```

Expected: one active `protect-release-tags` ruleset exists and targets `refs/tags/v*`.

- [ ] **Step 6: Verify live repository settings**

Run:

```bash
gh repo view EmmanuelCazarez/criteriaforge \
  --json defaultBranchRef,mergeCommitAllowed,squashMergeAllowed,rebaseMergeAllowed,deleteBranchOnMerge
gh api repos/EmmanuelCazarez/criteriaforge \
  --jq '{
    allow_auto_merge,
    squash_merge_commit_title,
    squash_merge_commit_message,
    security_and_analysis
  }'
gh api repos/EmmanuelCazarez/criteriaforge/rulesets \
  --jq '.[] | {id,name,target,enforcement}'
```

Expected:

- default branch `main`;
- merge commits `false`, squash `true`, rebase `false`;
- delete branch on merge `true`, auto-merge `false`;
- squash title `PR_TITLE`, message `PR_BODY`;
- secret scanning and push protection enabled;
- all three rulesets active.

- [ ] **Step 7: Test protection through a disposable pull request**

Create `ci/protection-smoke-test` from current `main`, make a documentation-only change, push it, and open `ci: verify protected main` targeting `main`.

Expected:

- direct merge is blocked until all seven required checks pass;
- the PR must be current with `main`;
- only squash merge is offered;
- after squash merge, the source branch disappears automatically;
- `main` gains exactly one commit.

Use a follow-up documentation correction with real value, such as recording the verified activation date, rather than an empty commit.

---

### Task 10: Retire `dev` and resolve every temporary branch safely

**Files:**
- No repository file changes required unless a separate public-API pull request is approved.

**Interfaces:**
- Consumes: default `main`, merged migration, active protection, branch inventory, and the separate decision for `cfca670`.
- Produces: `main` as the only permanent branch and no abandoned same-repository temporary branch.

- [ ] **Step 1: Prove all intended `dev` tree changes reached `main`**

Run:

```bash
git fetch --prune origin
git diff --name-status origin/main..origin/dev
git log --oneline origin/main..origin/dev
gh pr list --state open --base dev --json number,title,headRefName,url
gh pr list --state open --head dev --json number,title,baseRefName,url
```

Expected:

- no intended POM, workflow, Dependabot, or documentation change remains only on `dev`;
- no open pull request targets or originates from `dev`.

History may differ because the migration is intentionally squash-based; judge completion by reviewed tree content, not commit ancestry alone.

- [ ] **Step 2: Resolve the unique public-API branch in its dedicated task**

For `origin/feature/criteriaforge-library` at `cfca670`, choose exactly one reviewed outcome:

- Accepted: rebase or recreate the five record-assignment edits on a temporary branch from current `main`, run public-API tests, open a separate squash PR to `main`, merge it, and allow branch deletion.
- Rejected: record the user decision, confirm no open PR or dependent branch uses it, then delete the branch with explicit approval.

Do not include its Java changes in the repository-operations migration.

- [ ] **Step 3: Delete remote `dev` after the safety gates**

After Steps 1 and 2 are satisfied, run:

```bash
git push origin --delete dev
git fetch --prune origin
```

Expected: `origin/dev` no longer exists.

- [ ] **Step 4: Audit and remove other merged same-repository branches**

Run:

```bash
git branch --remotes --format='%(refname:short)'
gh pr list --state all --limit 100 \
  --json number,state,mergedAt,headRefName,headRepository,baseRefName,url
```

For each non-`main` same-repository branch, delete it only when:

- its PR is merged or its work is explicitly rejected;
- it has no unique accepted change;
- it is not the source of an open PR.

Expected final remote branch inventory: `origin/main` plus only branches belonging to active open pull requests.

- [ ] **Step 5: Verify release and publication invariants**

Run:

```bash
git tag --verify v0.1.0
git rev-parse v0.1.0^{commit}
gh api repos/EmmanuelCazarez/criteriaforge/environments/maven-central
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://repo1.maven.org/maven2/io/github/emmanuelcazarez/criteriaforge-spring-boot-starter/0.1.0/criteriaforge-spring-boot-starter-0.1.0.pom
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://repo1.maven.org/maven2/io/github/emmanuelcazarez/criteriaforge-example/0.1.0/criteriaforge-example-0.1.0.pom
```

Expected:

- `v0.1.0` remains valid and unchanged;
- `maven-central` still requires maintainer review and contains the release secrets without exposing values;
- starter `0.1.0` returns success;
- example `0.1.0` returns HTTP `404`, so the example remains GitHub-only.

- [ ] **Step 6: Produce the final operations report**

Report:

- migration PR URL and squash commit;
- successful required checks;
- live repository merge settings and ruleset IDs;
- `dev` deletion and final branch inventory;
- unchanged `v0.1.0` commit and signer fingerprint;
- Maven Central starter resolution and example exclusion;
- any branch deliberately retained for active work.

Do not include any secret value or environment-secret metadata beyond the secret names already documented.
