# Projection Alias ReDoS 0.1.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the polynomial projection-alias parser behavior, close GitHub CodeQL alert 1, and publish CriteriaForge 0.1.1 as a signed patch release.

**Architecture:** Replace the backtracking alias regex with a deterministic scanner that recognizes the same ASCII case-insensitive `as` delimiter and Java default-regex whitespace set in linear time. Deliver the fix through protected `dev`, prepare stable 0.1.1 metadata on a release branch, create one squash release commit on `main`, publish from a signed tag, then reset `dev` and begin 0.1.2-SNAPSHOT development.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Maven Wrapper, Spring Boot 3.5.16 and 4.1.0, PostgreSQL/Testcontainers, GitHub Actions, CodeQL, GPG-signed Git tags, Maven Central.

## Global Constraints

- Work synchronously with explicit checkpoints; do not dispatch subagents.
- Preserve projection alias syntax and existing validation behavior.
- Production parsing must be linear in the input token length and must not use a replacement regular expression.
- Temporary branches target protected `dev`; only exact `dev` may target `main`.
- All pull requests use squash merge and same-repository temporary branches are deleted after merge.
- The release version is exactly `0.1.1`; the next development version is exactly `0.1.2-SNAPSHOT`.
- Do not expose signing keys, Maven Central credentials, environment secrets, or passphrases.
- `criteriaforge-example` remains excluded from Maven Central.
- Do not create or push `v0.1.1` until the exact `main` commit passes post-merge CI and CodeQL.
- Do not reset `dev` until Maven Central publication is confirmed.

---

### Task 1: Implement deterministic projection alias parsing with TDD

**Files:**
- Modify: `criteriaforge-spring-web/src/test/java/io/github/emmanuelcazarez/criteriaforge/web/DefaultQueryParameterParserTest.java`
- Modify: `criteriaforge-spring-web/src/main/java/io/github/emmanuelcazarez/criteriaforge/web/DefaultQueryParameterParser.java:105-114`

**Interfaces:**
- Consumes: `DefaultQueryParameterParser.parse(MultiValueMap<String, String>)` and `QueryRequest.Builder.selectAs(String, String)`.
- Produces: private `aliasSeparator(String): int` and `isAliasWhitespace(char): boolean`; no public API change.

- [ ] **Step 1: Add the failing adversarial regression test and syntax-preservation test**

Add these imports and tests to `DefaultQueryParameterParserTest`:

```java
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

@Test
void handlesAdversarialProjectionWhitespaceWithinOneSecond() {
    var parameters = new LinkedMultiValueMap<String, String>();
    parameters.add("fields", "a" + " ".repeat(50_000) + "b");

    assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
        assertThatThrownBy(() -> parser.parse(parameters))
            .isInstanceOf(IllegalArgumentException.class));
}

@Test
void preservesCaseInsensitiveAliasAndNonSpaceWhitespace() {
    var parameters = new LinkedMultiValueMap<String, String>();
    parameters.add("fields", "customerName\tAS\tbuyer.name");

    var parsed = parser.parse(parameters);

    assertThat(parsed.fields()).containsExactly(
        ProjectionField.aliased("customerName", "buyer.name"));
}
```

- [ ] **Step 2: Run the adversarial test and verify RED**

Run:

```bash
./mvnw -q -pl criteriaforge-spring-web -am \
  -Dtest=DefaultQueryParameterParserTest#handlesAdversarialProjectionWhitespaceWithinOneSecond \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the current regex exceeds the one-second timeout. If it unexpectedly passes, increase only the repeated-space count until the existing regex fails consistently; do not lower the timeout below one second.

- [ ] **Step 3: Replace the regex with the minimal linear scanner**

Replace `select` and add the two helpers:

```java
private static void select(QueryRequest.Builder builder, String token) {
    var separator = aliasSeparator(token);
    if (separator >= 0) {
        builder.selectAs(
            token.substring(0, separator).trim(),
            token.substring(separator + 2).trim());
    } else {
        builder.select(token);
    }
}

private static int aliasSeparator(String token) {
    for (var index = 1; index + 2 < token.length(); index++) {
        var first = token.charAt(index);
        var second = token.charAt(index + 1);
        if ((first == 'a' || first == 'A')
                && (second == 's' || second == 'S')
                && isAliasWhitespace(token.charAt(index - 1))
                && isAliasWhitespace(token.charAt(index + 2))) {
            return index;
        }
    }
    return -1;
}

private static boolean isAliasWhitespace(char value) {
    return switch (value) {
        case ' ', '\t', '\n', '\u000B', '\f', '\r' -> true;
        default -> false;
    };
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```bash
./mvnw -q -pl criteriaforge-spring-web -am \
  -Dtest=DefaultQueryParameterParserTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, including the timeout and mixed-case/tab alias tests.

- [ ] **Step 5: Run module verification and commit**

Run:

```bash
./mvnw -q -pl criteriaforge-spring-web -am verify
git diff --check
git add criteriaforge-spring-web/src/main/java/io/github/emmanuelcazarez/criteriaforge/web/DefaultQueryParameterParser.java \
  criteriaforge-spring-web/src/test/java/io/github/emmanuelcazarez/criteriaforge/web/DefaultQueryParameterParserTest.java
git commit -m "fix: make projection alias parsing linear"
```

Expected: verification passes and the commit contains only the parser and tests.

### Task 2: Verify and merge the security fix into dev

**Files:**
- Verify: all reactor modules and `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: the Task 1 implementation and the committed design document.
- Produces: one squash commit on `dev` and a deleted remote `fix/codeql-polynomial-redos` branch.

- [ ] **Step 1: Run the complete local matrix**

Run each command separately and require exit code zero:

```bash
./mvnw -q verify
./mvnw -B -ntp verify -Dspring-boot.version=3.5.16
./mvnw -B -ntp verify -Dspring-boot.version=4.1.0
./mvnw -B -ntp -Ppostgresql-tests verify
./mvnw -B -ntp -Pquality verify
```

- [ ] **Step 2: Recheck branch freshness and push**

Run:

```bash
git fetch --prune origin
git merge-base --is-ancestor origin/dev HEAD
git status --short --branch
git push -u origin fix/codeql-polynomial-redos
```

Expected: clean branch, current `dev` ancestor, push succeeds without force.

- [ ] **Step 3: Open the fix PR to dev**

Create the PR with:

```text
Base: dev
Head: fix/codeql-polynomial-redos
Title: fix: prevent projection alias ReDoS
Body:
## Summary
- replace the polynomial projection-alias regex with a linear scanner
- preserve case-insensitive AS and existing whitespace semantics
- add an adversarial regression test for CodeQL alert 1

## Verification
- Spring Web focused tests
- full Maven reactor
- Java 17 with Spring Boot 3.5.16 and 4.1.0
- PostgreSQL and quality profiles
```

- [ ] **Step 4: Require every protected check and squash-merge**

Wait for `pr-policy`, both Java/Boot builds, `postgresql`, `quality`, `dependency-review`, and `codeql-java`. Inspect failed logs rather than retrying blindly. When all pass, squash-merge and verify the remote source branch is deleted.

- [ ] **Step 5: Verify dev contains the fix**

Run:

```bash
git fetch --prune origin
git show origin/dev:criteriaforge-spring-web/src/main/java/io/github/emmanuelcazarez/criteriaforge/web/DefaultQueryParameterParser.java | rg "aliasSeparator|isAliasWhitespace"
git ls-remote --heads origin fix/codeql-polynomial-redos
```

Expected: helper names are present and the branch lookup is empty.

### Task 3: Prepare stable 0.1.1 on dev

**Files:**
- Modify: `pom.xml`
- Modify: `criteriaforge-core/pom.xml`
- Modify: `criteriaforge-jpa/pom.xml`
- Modify: `criteriaforge-spring-web/pom.xml`
- Modify: `criteriaforge-spring-boot-autoconfigure/pom.xml`
- Modify: `criteriaforge-spring-boot-starter/pom.xml`
- Modify: `criteriaforge-example/pom.xml`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: the squash-merged fix on `origin/dev`.
- Produces: stable Maven reactor version `0.1.1` and dated changelog entry `## [0.1.1] - 2026-08-03` on `dev`.

- [ ] **Step 1: Create the release branch from current dev**

Run:

```bash
git fetch --prune origin
git switch -c release/0.1.1 origin/dev
```

- [ ] **Step 2: Set every reactor POM to 0.1.1**

Run:

```bash
./mvnw -q versions:set -DnewVersion=0.1.1 -DgenerateBackupPoms=false
```

Verify no snapshot remains in reactor POMs:

```bash
if rg -n '0\.1\.1-SNAPSHOT' --glob 'pom.xml'; then exit 1; fi
```

- [ ] **Step 3: Add the 0.1.1 changelog entry and links**

Change the top of `CHANGELOG.md` to:

```markdown
## [Unreleased]

## [0.1.1] - 2026-08-03

### Security

- Replaced projection-alias regular-expression parsing with a deterministic
  linear-time scanner to prevent polynomial denial of service from malicious
  `fields` query parameters.
```

Change the comparison links to:

```markdown
[Unreleased]: https://github.com/EmmanuelCazarez/criteriaforge/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/EmmanuelCazarez/criteriaforge/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/EmmanuelCazarez/criteriaforge/releases/tag/v0.1.0
```

- [ ] **Step 4: Validate release metadata and build**

Run:

```bash
project_version="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)"
.github/scripts/validate-release-pr.sh "chore(release): release 0.1.1" "${project_version}" CHANGELOG.md
./mvnw -q verify
git diff --check
```

Expected: metadata accepted, project version exactly `0.1.1`, all tests pass.

- [ ] **Step 5: Commit, push, and merge the release-preparation PR**

Commit with:

```bash
git add pom.xml */pom.xml CHANGELOG.md
git commit -m "chore(release): prepare 0.1.1"
git push -u origin release/0.1.1
```

Open `release/0.1.1 -> dev` titled `chore(release): prepare 0.1.1`. Require all protected checks, squash-merge, and verify the remote release branch is deleted.

### Task 4: Create the single 0.1.1 release commit on main

**Files:**
- Verify: exact tree and version from `origin/dev`

**Interfaces:**
- Consumes: stable `0.1.1` on protected `dev`.
- Produces: one squash commit on `main`, with no tag yet.

- [ ] **Step 1: Open the exact release PR**

Create `dev -> main` with exact title:

```text
chore(release): release 0.1.1
```

The body must summarize the ReDoS fix, compatibility matrix, and Maven Central publication intent.

- [ ] **Step 2: Verify all release checks**

Require the seven protected checks and confirm `pr-policy` reports accepted release metadata for `0.1.1`. Confirm the PR head remains exact `dev` and contains the latest `main` ancestor before merge.

- [ ] **Step 3: Squash-merge and record the immutable candidate SHA**

Squash-merge without deleting permanent `dev`. Fetch `main`, record its SHA, and verify its tree equals the pre-merge `dev` tree:

```bash
git fetch --prune origin main dev
RELEASE_COMMIT="$(git rev-parse origin/main)"
test "$(git rev-parse origin/main^{tree})" = "$(git rev-parse origin/dev^{tree})"
printf 'release commit=%s\n' "${RELEASE_COMMIT}"
```

- [ ] **Step 4: Wait for post-merge CI and CodeQL**

Wait for push-triggered CI and CodeQL on exactly `${RELEASE_COMMIT}`. Do not tag if any job is pending, skipped unexpectedly, cancelled, or failed. Query CodeQL alert 1 and require it to be fixed after the main analysis completes.

### Task 5: Sign, publish, and verify CriteriaForge 0.1.1

**Files:**
- Verify: `.github/release-signing-key.asc`
- Verify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: the green `RELEASE_COMMIT` on `main` and the maintainer signing identity.
- Produces: immutable signed tag `v0.1.1`, Maven Central artifacts, and GitHub release notes.

- [ ] **Step 1: Perform pre-tag collision and signing checks**

Run:

```bash
git fetch --prune origin main --tags
test -z "$(git ls-remote --tags origin v0.1.1 'v0.1.1^{}')"
git switch --detach origin/main
test "$(git rev-parse HEAD)" = "${RELEASE_COMMIT}"
test "$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)" = "0.1.1"
gpg --list-secret-keys --with-colons 27CE82768E118D3F80256BB6E60E5A6A6709E150
```

- [ ] **Step 2: Create and verify the signed tag locally**

Run at exact `${RELEASE_COMMIT}`:

```bash
git switch --detach "${RELEASE_COMMIT}"
git tag -s v0.1.1 -m "CriteriaForge 0.1.1" "${RELEASE_COMMIT}"
git tag -v v0.1.1
.github/scripts/validate-release-candidate.sh \
  v0.1.1 \
  27CE82768E118D3F80256BB6E60E5A6A6709E150 \
  .github/release-signing-key.asc
```

Expected: good signature, exact fingerprint, stable version, and candidate reachable from `origin/main`.

- [ ] **Step 3: Push the tag and approve only the intended environment deployment**

Push only `v0.1.1`, identify the tag-triggered Release run, wait for candidate and matrix jobs, then approve its pending `maven-central` deployment. Never print secret values.

```bash
git push origin v0.1.1
```

- [ ] **Step 4: Verify upload and complete manual Central publication**

Require the Release workflow to finish successfully. In Maven Central, verify the deployment contains `criteriaforge-core`, `criteriaforge-jpa`, `criteriaforge-spring-web`, `criteriaforge-spring-boot-autoconfigure`, and `criteriaforge-spring-boot-starter`, with POM, JAR, sources, Javadocs, checksums, and signatures. Confirm `criteriaforge-example` is absent, then publish the deployment.

- [ ] **Step 5: Verify public artifacts and create release notes**

Poll Maven Central until these return HTTP 200:

```text
criteriaforge-core/0.1.1/criteriaforge-core-0.1.1.pom
criteriaforge-jpa/0.1.1/criteriaforge-jpa-0.1.1.pom
criteriaforge-spring-web/0.1.1/criteriaforge-spring-web-0.1.1.pom
criteriaforge-spring-boot-autoconfigure/0.1.1/criteriaforge-spring-boot-autoconfigure-0.1.1.pom
criteriaforge-spring-boot-starter/0.1.1/criteriaforge-spring-boot-starter-0.1.1.pom
```

Require the example POM to return 404. Create GitHub release `v0.1.1` with a concise security note and upgrade coordinates only after Central is public.

### Task 6: Reset dev and start 0.1.2-SNAPSHOT

**Files:**
- Verify: `docs/branching.md:62-162`
- Modify on next-snapshot branch: all reactor `pom.xml` files

**Interfaces:**
- Consumes: published `v0.1.1`, exact `origin/main`, live `protect-dev` ruleset.
- Produces: recreated `dev` at the release commit, then one squash commit advancing dev to `0.1.2-SNAPSHOT`.

- [ ] **Step 1: Run every documented reset precondition**

Verify Central is public, `main` and `dev` trees are equal, no PR targeting `dev` is open, the exact `protect-dev` ruleset is active, and its full JSON definition is saved in a temporary directory.

- [ ] **Step 2: Execute the failure-safe controlled dev reset exactly as documented**

Run the complete shell procedure under `docs/branching.md` heading `Controlled dev reset after a release` without modification. It must disable only `protect-dev`, recreate `dev` at `origin/main`, and restore the exact ruleset through its exit trap. Stop immediately if final enforcement is not `active`.

- [ ] **Step 3: Advance the next snapshot through a temporary branch**

Run:

```bash
git fetch --prune origin dev
git switch -c chore/next-snapshot origin/dev
./mvnw -q versions:set -DnewVersion=0.1.2-SNAPSHOT -DgenerateBackupPoms=false
./mvnw -q verify
git add pom.xml */pom.xml
git commit -m "chore: start 0.1.2 development"
git push -u origin chore/next-snapshot
```

Open `chore/next-snapshot -> dev` titled `chore: start 0.1.2 development`, require all protected checks, squash-merge, and verify automatic branch deletion.

### Task 7: Final security and release audit

**Files:**
- Verify only; no repository changes.

**Interfaces:**
- Consumes: published 0.1.1 and post-release dev.
- Produces: evidence-backed completion report.

- [ ] **Step 1: Audit GitHub state**

Verify only `main` and `dev` remain remotely, no PR is open, `main` points to the 0.1.1 release commit, `dev` is one next-snapshot commit ahead, all four branch/tag rulesets remain active, and CodeQL alert 1 is fixed.

- [ ] **Step 2: Audit release identity and artifacts**

Verify `git tag -v v0.1.1`, the tag target equals the release commit, GitHub release notes exist, all intended Maven Central artifacts resolve, the example remains absent, and no credentials appeared in logs or committed files.

- [ ] **Step 3: Run final checkout verification**

Fast-forward the normal local `dev` checkout and run:

```bash
./mvnw -q verify
git status --short --branch
```

Expected: tests pass and the checkout is clean at current `origin/dev`.
