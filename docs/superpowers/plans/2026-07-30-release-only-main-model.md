# Release-Only Main Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert CriteriaForge from the temporary trunk-based workflow to a release-ledger model in which `dev` collects reviewed integration work and every future commit added to `main` represents one official Maven Central release.

**Architecture:** Keep the existing signed `v0.1.0` tag and 38-commit `main` history intact, merge one final governance correction as the explicit legacy exception, then recreate `dev` from that corrected baseline. The bootstrap is an exact same-repository `dev`-to-`main` tuple pinned to the pre-migration `main` SHA, so it expires automatically when `main` moves. CI otherwise enforces source-branch and stable release-metadata contracts: approved short-lived branches squash into `dev`; only `dev` can open an exact release PR to `main`; the release squash is the single official release commit; and `dev` is recreated from `main` after each release to eliminate squash ancestry divergence.

**Tech Stack:** Git/GitHub, GitHub Actions, GitHub rulesets and repository settings, POSIX shell, Maven Wrapper, Java 17, Spring Boot 3 and 4, PostgreSQL, CodeQL, Dependabot, Maven Central, GPG-signed Git tags.

## Global Constraints

- Preserve the existing `main` history. Do not rewrite or force-push `main`.
- Preserve signed tag `v0.1.0`, its signature, and its target commit.
- Treat the governance PR described below as the final non-release commit allowed on `main`.
- Permit that one-time PR only for the exact title, repositories, branches, and base SHA recorded in Task 7; never broaden or refresh the pinned exception.
- After that governance merge, only a squash-merged `dev` release PR may update `main`.
- Keep `main` as the default branch and `dev` as the permanent between-release integration branch.
- Use squash merge for every accepted PR. Keep merge commits and rebase merges disabled.
- Automatically delete every merged short-lived source branch.
- Delete and recreate `dev` from the new `main` tip after each official release; do not manually merge `main` back into the old `dev`.
- Dependabot owns and refreshes its temporary branches. Humans do not keep those branches synchronized.
- Never expose secret values, GPG private material, Maven Central credentials, or environment-protected data.
- Do not publish a release while performing this migration.
- Do not change the public CriteriaForge API or documentation examples except where this governance migration explicitly requires repository-process documentation.
- Preserve the exact required status-check contexts unless a task explicitly changes both workflows and rulesets:
  - `pr-policy`
  - `build-java17-boot3`
  - `build-java17-boot4`
  - `postgresql`
  - `quality`
  - `dependency-review`
  - `codeql-java`
- Before deleting or recreating any remote branch, prove that no unique work or open PR depends on it and record the evidence.
- Stage only the files named by each task. Preserve unrelated user changes.
- Use checked-in `./mvnw` for Maven verification.
- A quiet Spring or Maven process is not evidence of success; rely on exit codes and explicit verification output.

---

## Task 1: Establish the migration baseline and supersession record

**Files:**

- Modify: `docs/superpowers/specs/2026-07-29-trunk-release-and-repository-protection-design.md`
- Modify: `docs/superpowers/plans/2026-07-29-trunk-release-and-repository-protection.md`
- Verify: `docs/superpowers/specs/2026-07-30-release-only-main-model-design.md`
- Verify: `docs/superpowers/plans/2026-07-30-release-only-main-model.md`

- [ ] **Step 1: Capture immutable baseline evidence**

Run:

```bash
git fetch --prune origin
git status --short --branch
git rev-list --count origin/main
git log -1 --format='%H %s' origin/main
git show-ref --verify refs/tags/v0.1.0
git merge-base --is-ancestor v0.1.0 origin/main
git tag -v v0.1.0
gh api repos/EmmanuelCazarez/criteriaforge/collaborators --paginate --jq '.[] | [.login, .permissions.admin, .permissions.maintain, .permissions.push] | @tsv'
gh pr list --state open --json number,title,headRefName,baseRefName,url
git branch -r
```

Expected:

- Working tree is clean before implementation.
- `origin/main` has 38 commits and points to `cab27f0...` before the governance merge.
- `v0.1.0` resolves to the existing signed release target and is an ancestor of `origin/main`.
- The collaborator audit confirms whether zero required approvals is necessary; as of planning, only `EmmanuelCazarez` is eligible.
- Open PR and remote branch output identifies the current Dependabot PR and the old feature branch.

- [ ] **Step 2: Prove that the old remote `dev` contributes no unique intended tree changes**

Run:

```bash
git log --left-right --cherry-pick --oneline origin/main...origin/dev
git diff --stat origin/dev..origin/main
git diff --stat origin/main..origin/dev
gh pr list --state open --base dev --json number,title,headRefName,url
gh pr list --state open --head dev --json number,title,baseRefName,url
```

Expected:

- Any old `dev` work already exists in `main`, or discrepancies are fully explained before proceeding.
- No open PR currently depends on `dev`.
- If unique intended work is found, stop; do not delete or recreate `dev`.

- [ ] **Step 3: Mark the trunk documents as superseded**

Add a prominent note below each old document title:

```markdown
> Superseded on 2026-07-30 by the approved release-only `main` model. Retained as historical context; do not execute this document.
```

Link the note to:

```text
docs/superpowers/specs/2026-07-30-release-only-main-model-design.md
docs/superpowers/plans/2026-07-30-release-only-main-model.md
```

- [ ] **Step 4: Verify references and placeholder-free content**

Run:

```bash
rg -n "Superseded on 2026-07-30|release-only.*main" docs/superpowers
rg -n 'TO''DO|T''BD|FIX''ME|PLACE''HOLDER' docs/superpowers/specs/2026-07-30-release-only-main-model-design.md docs/superpowers/plans/2026-07-30-release-only-main-model.md
```

Expected:

- Both prior trunk documents clearly point to the approved replacement.
- The second command returns no unresolved placeholders.

- [ ] **Step 5: Commit the documentation state**

```bash
git add docs/superpowers/specs/2026-07-29-trunk-release-and-repository-protection-design.md docs/superpowers/plans/2026-07-29-trunk-release-and-repository-protection.md docs/superpowers/specs/2026-07-30-release-only-main-model-design.md docs/superpowers/plans/2026-07-30-release-only-main-model.md
git commit -m "docs: supersede trunk workflow guidance"
```

Expected: one documentation-only commit on `docs/release-only-main-model`.

---

## Task 2: Enforce destination-specific pull-request policy

**Files:**

- Modify: `.github/scripts/validate-pr-policy.sh`
- Modify: `.github/scripts/validate-pr-policy-test.sh`

- [ ] **Step 1: Add failing tests for the approved branch matrix**

Extend the test harness to pass `base_ref` and `base_sha` as the fifth and sixth arguments.

Add passing cases:

- `feature/*`, `fix/*`, `docs/*`, `refactor/*`, `test/*`, `build/*`, `ci/*`, `chore/*`, `release/*`, and `dependabot/*` from the same repository to `dev`, with Conventional Commit PR titles.
- A fork branch to `dev`, with a Conventional Commit PR title.
- Exact same-repository `dev` to `main` with `chore(release): release 0.2.0`.
- A normal release title against a different nonempty base SHA, proving releases remain valid after bootstrap expiry.
- The exact one-time same-repository `dev`-to-`main` governance title against base SHA `cab27f008b664df78ac83247f3ad27cf160fa72e`.

Add failing cases:

- Any branch except exact `dev` targeting `main`.
- `dev` from a fork targeting `main`.
- `dev` to `main` with a non-release title.
- The governance title with any different base SHA.
- The governance title with a wrong head ref.
- The governance title with a wrong head repository.
- The governance title with a wrong base repository.
- The governance title with a wrong base ref.
- A near-match governance title.
- The governance tuple with a missing base SHA.
- A release title with `-SNAPSHOT`, missing patch number, leading-zero numeric parts, or extra text.
- A same-repository branch with an unapproved prefix targeting `dev`.
- An unsupported base branch.

Run:

```bash
bash .github/scripts/validate-pr-policy-test.sh
```

Expected: the new bootstrap passing case fails against the pre-bootstrap validator.

- [ ] **Step 2: Implement base-aware policy validation**

Change the validator contract to:

```text
validate-pr-policy.sh TITLE HEAD_REF HEAD_REPOSITORY BASE_REPOSITORY BASE_REF BASE_SHA
```

Implement these branches:

```text
base=dev:
  require a Conventional Commit PR title
  for same-repository branches, require an approved short-lived prefix
  allow forks because their namespace is contributor-owned

base=main:
  require same repository
  require exact head_ref=dev
  accept exact title pattern chore(release): release X.Y.Z
  or accept the exact one-time title ci: adopt release-only main governance
    only when base_sha=cab27f008b664df78ac83247f3ad27cf160fa72e
  reject every other title or bootstrap base SHA

other base:
  reject
```

Use a SemVer core pattern that rejects leading zeroes:

```text
(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)
```

- [ ] **Step 3: Run the policy tests**

```bash
bash .github/scripts/validate-pr-policy-test.sh
```

Expected: all branch and title cases pass.

- [ ] **Step 4: Perform direct negative checks**

```bash
bootstrap_sha=cab27f008b664df78ac83247f3ad27cf160fa72e
if bash .github/scripts/validate-pr-policy.sh "ci: bypass" "ci/bypass" "EmmanuelCazarez/criteriaforge" "EmmanuelCazarez/criteriaforge" "main" "${bootstrap_sha}"; then exit 1; fi
if bash .github/scripts/validate-pr-policy.sh "chore(release): release 0.2.0" "dev" "someone/fork" "EmmanuelCazarez/criteriaforge" "main" "${bootstrap_sha}"; then exit 1; fi
if bash .github/scripts/validate-pr-policy.sh "ci: adopt release-only main governance" "dev" "EmmanuelCazarez/criteriaforge" "EmmanuelCazarez/criteriaforge" "main" "0000000000000000000000000000000000000000"; then exit 1; fi
bash .github/scripts/validate-pr-policy.sh "chore(release): release 0.2.0" "dev" "EmmanuelCazarez/criteriaforge" "EmmanuelCazarez/criteriaforge" "main" "${bootstrap_sha}"
bash .github/scripts/validate-pr-policy.sh "ci: adopt release-only main governance" "dev" "EmmanuelCazarez/criteriaforge" "EmmanuelCazarez/criteriaforge" "main" "${bootstrap_sha}"
```

Expected: the first three commands are rejected; the final two succeed.

- [ ] **Step 5: Commit the policy validator**

```bash
git add .github/scripts/validate-pr-policy.sh .github/scripts/validate-pr-policy-test.sh
git commit -m "ci: enforce dev and release pull request sources"
```

---

## Task 3: Validate release PR title, Maven version, and changelog atomically

**Files:**

- Create: `.github/scripts/validate-release-pr.sh`
- Create: `.github/scripts/validate-release-pr-test.sh`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Write failing release-metadata tests**

Define:

```text
validate-release-pr.sh TITLE PROJECT_VERSION CHANGELOG_FILE
```

Add tests proving:

- `chore(release): release 0.2.0`, project version `0.2.0`, and changelog heading `## [0.2.0] - YYYY-MM-DD` pass.
- A title/version mismatch fails.
- A `-SNAPSHOT` project version fails.
- A missing dated changelog heading fails.
- A malformed release title fails.

Run:

```bash
bash .github/scripts/validate-release-pr-test.sh
```

Expected: failure because the implementation script does not exist yet.

- [ ] **Step 2: Implement release metadata validation**

The script must:

1. Extract exact `X.Y.Z` from the approved PR title.
2. Require `PROJECT_VERSION` to equal that version exactly.
3. Reject snapshot, prerelease, and metadata suffixes for this stable-release flow.
4. Require a dated `## [X.Y.Z] - YYYY-MM-DD` heading in `CHANGELOG.md`.
5. Emit actionable errors without printing credentials or environment data.

- [ ] **Step 3: Run tests**

```bash
bash .github/scripts/validate-release-pr-test.sh
bash .github/scripts/validate-pr-policy-test.sh
```

Expected: both test suites pass.

- [ ] **Step 4: Wire both validators into `pr-policy`**

Update `.github/workflows/ci.yml` so:

- Pull requests and pushes to both `dev` and `main` run CI.
- Pull request `edited` activity reruns CI so a title change re-evaluates policy.
- The existing `pr-policy` job passes `${{ github.base_ref }}` and `${{ github.event.pull_request.base.sha }}` as the fifth and sixth policy arguments.
- For PRs targeting `main` other than the exact governance title, Java 17 is available and the root Maven version is resolved using the wrapper:

```bash
./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout
```

- The main-targeting PR then invokes `validate-release-pr.sh` with the PR title, resolved project version, and `CHANGELOG.md`. Only the exact governance title skips these release-metadata steps; `pr-policy` still rejects that title unless the complete bootstrap tuple matches.
- The job context remains exactly `pr-policy`.
- `validate-pr-policy-test.sh` persistently parses both metadata-step conditions, proves the exact governance title skips them, and proves a normal `main` release title runs them.

- [ ] **Step 5: Lint and inspect the workflow**

Run:

```bash
ruby -e 'require "yaml"; YAML.load_file(".github/workflows/ci.yml", aliases: true); puts "ci yaml ok"'
rg -n "branches:|github.base_ref|validate-release-pr|help:evaluate|pr-policy" .github/workflows/ci.yml
```

Expected:

- YAML parses.
- Both permanent branches are covered.
- Release metadata validation runs for `main` pull requests except the exact one-time governance title.

- [ ] **Step 6: Commit release PR validation**

```bash
git add .github/scripts/validate-release-pr.sh .github/scripts/validate-release-pr-test.sh .github/workflows/ci.yml
git commit -m "ci: validate release pull request metadata"
```

---

## Task 4: Align CI, CodeQL, Dependabot, and stored ruleset definitions

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/codeql.yml`
- Verify: `.github/workflows/release.yml`
- Modify: `.github/dependabot.yml`
- Modify: `.github/rulesets/protect-main.json`
- Create: `.github/rulesets/protect-dev.json`

- [ ] **Step 1: Add static assertions for permanent-branch coverage**

Run assertions that intentionally fail before the edits:

```bash
rg -n "main|dev" .github/workflows/ci.yml .github/workflows/codeql.yml .github/dependabot.yml
test -f .github/rulesets/protect-dev.json
```

Expected: the `protect-dev.json` assertion fails and current configuration reveals `main`-only targets.

- [ ] **Step 2: Update CI and CodeQL branch triggers**

For both `ci.yml` and `codeql.yml`, use:

```yaml
branches:
  - main
  - dev
```

for relevant `pull_request` and `push` triggers.

Keep the existing job names and matrix:

- Java 17 / Spring Boot 3
- Java 17 / Spring Boot 4
- PostgreSQL integration
- quality checks
- dependency review
- CodeQL Java

Update `actions/dependency-review-action` from `v4` to `v5`, incorporating the currently pending Dependabot change into the governance PR so that its obsolete `main`-targeting PR can later be closed safely.

Do not make a merge to `main` publish artifacts. Verify that `.github/workflows/release.yml` remains triggered by a pushed signed `v*.*.*` tag, with `workflow_dispatch` available only to rerun an existing signed release version.

- [ ] **Step 3: Retarget Dependabot**

Set `target-branch: dev` for both:

- Maven version updates
- GitHub Actions version updates

Keep:

- weekly scheduling
- Spring Boot semantic-major ignores so Boot 3 remains the baseline
- automated security PRs disabled
- auto-merge disabled

- [ ] **Step 4: Add the `dev` ruleset source**

Create `.github/rulesets/protect-dev.json` with:

- target `refs/heads/dev`
- active enforcement
- branch deletion protection
- non-fast-forward protection
- required linear history
- pull request required
- zero required approvals while EmmanuelCazarez is the sole eligible maintainer
- conversation resolution
- a documented future hardening step to require one approval when a second eligible maintainer exists
- squash as the only permitted merge method
- exact seven required status checks
- strict status checks
- `do_not_enforce_on_create: false` during normal operation
- no bypass actor; the ruleset is temporarily disabled only inside the SHA-gated post-release reset and is reactivated immediately

Keep `.github/rulesets/protect-main.json` aligned with the same check contexts and squash-only behavior. Keep its approval count at zero for the current sole-maintainer repository, and document raising both permanent-branch rulesets to one required approval when a second eligible maintainer is available. Do not weaken signed release-tag rules.

- [ ] **Step 5: Validate YAML and JSON**

```bash
ruby -e 'require "yaml"; %w[.github/workflows/ci.yml .github/workflows/codeql.yml .github/workflows/release.yml .github/dependabot.yml].each { |f| YAML.load_file(f, aliases: true); puts "#{f} ok" }'
jq empty .github/rulesets/protect-main.json .github/rulesets/protect-dev.json .github/rulesets/protect-release-tags.json .github/rulesets/restrict-release-tag-creation.json
jq -r '.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context' .github/rulesets/protect-main.json
jq -r '.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context' .github/rulesets/protect-dev.json
```

Expected:

- All YAML and JSON parse.
- `main` and `dev` list the exact same seven required contexts.
- Existing release tag controls remain unchanged.

- [ ] **Step 6: Commit platform-source configuration**

```bash
git add .github/workflows/ci.yml .github/workflows/codeql.yml .github/dependabot.yml .github/rulesets/protect-main.json .github/rulesets/protect-dev.json
git commit -m "ci: align automation with release-only main"
```

---

## Task 5: Rewrite contributor and release procedures

**Files:**

- Modify: `CONTRIBUTING.md`
- Modify: `RELEASING.md`
- Modify: `docs/branching.md`
- Verify: `CHANGELOG.md`
- Verify: `pom.xml`

- [ ] **Step 1: Replace trunk-only contributor guidance**

Document:

- `main` is the official release ledger and default branch.
- `dev` is the integration branch.
- Contributors branch from current `dev`.
- Same-repository branch prefixes are restricted to the approved list.
- All PRs to `dev` use Conventional Commit titles and squash merge.
- Merged short-lived branches are deleted automatically.
- Only exact `dev` may target `main`.

- [ ] **Step 2: Document the release lifecycle exactly**

Rewrite release steps:

1. Ensure `dev` is green and all intended work is integrated.
2. Create `release/X.Y.Z` from `dev`.
3. Set the reactor version to stable `X.Y.Z`; update the dated changelog.
4. Merge `release/X.Y.Z` into `dev` by squash; delete the release branch.
5. Open `dev` to `main` with exact title `chore(release): release X.Y.Z`.
6. Require all checks and complete maintainer review; no approval is required while only one eligible maintainer exists.
7. Squash merge once, creating the sole release commit on `main`.
8. Wait for post-merge CI and CodeQL on that exact `main` commit.
9. Create and locally verify the signed `vX.Y.Z` tag on that exact commit, then push the tag.
10. Let the tag-push-triggered release workflow validate the signed candidate and upload the intended Maven Central artifacts; `workflow_dispatch` may rerun an existing signed tag but does not replace it.
11. Delete and recreate `dev` from the new `main` tip.
12. Create a short-lived `chore/*` branch from new `dev`, set the next snapshot version, and squash it back into `dev`.

Explain that `0.2.0` is appropriate when backward-compatible features are introduced; fixes/refactors alone normally use a patch increment.

Add the approved branch flowchart and an exact automation inventory:

1. `CI`: pull requests and pushes on `dev` and `main`, plus manual dispatch; policy, Java 17/Boot 3, Java 17/Boot 4, PostgreSQL, quality, and dependency review.
2. `CodeQL`: pull requests and pushes on `dev` and `main`, weekly schedule, plus manual dispatch.
3. `Release`: pushed signed `v*.*.*` tags, plus manual rerun for an existing signed version; a merge to `main` alone does not publish. Its protected `maven-central` job uploads the signed bundle, and Central publication remains a separate manual approval.
4. `Dependabot`: weekly Maven and GitHub Actions update automation targeting `dev`; describe it separately from the three GitHub Actions workflows.

- [ ] **Step 3: Document the historical exception**

State:

- The first 38 `main` commits and signed `v0.1.0` are retained as legacy history.
- The governance migration adds one final non-release correction commit.
- The one-release/one-commit invariant starts at the resulting freeze baseline.
- No claim is made that historical `main` retroactively contains only release commits.

- [ ] **Step 4: Verify docs agree**

```bash
rg -n 'branch from `main`|target `main`|only permanent branch|no `dev`' CONTRIBUTING.md RELEASING.md docs/branching.md
rg -n "release ledger|integration branch|chore\\(release\\): release|recreate.*dev|squash" CONTRIBUTING.md RELEASING.md docs/branching.md
```

Expected:

- The first search returns no active trunk-only instructions.
- The second confirms all central invariants are documented.

- [ ] **Step 5: Commit process documentation**

```bash
git add CONTRIBUTING.md RELEASING.md docs/branching.md
git commit -m "docs: document release-ledger branch workflow"
```

---

## Task 6: Run the complete local governance and build gate

**Files:**

- Verify all files changed in Tasks 1–5.

- [ ] **Step 1: Run shell policy suites**

```bash
bash .github/scripts/validate-pr-policy-test.sh
bash .github/scripts/validate-release-pr-test.sh
bash .github/scripts/validate-release-candidate-test.sh
```

Expected: all suites pass.

- [ ] **Step 2: Validate configuration syntax and check-name parity**

```bash
ruby -e 'require "yaml"; Dir[".github/workflows/*.yml"].each { |f| YAML.load_file(f, aliases: true); puts "#{f} ok" }'
jq empty .github/rulesets/*.json .github/repository-settings.json
diff -u \
  <(jq -r '.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context' .github/rulesets/protect-main.json | sort) \
  <(jq -r '.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context' .github/rulesets/protect-dev.json | sort)
```

Expected: syntax passes and no status-check difference is printed.

- [ ] **Step 3: Run the Maven verification**

```bash
./mvnw -B -ntp clean verify
```

Expected: reactor succeeds with no skipped or failed required module.

- [ ] **Step 4: Verify the Spring Boot compatibility builds**

Run the same Maven commands used by:

- `build-java17-boot3`
- `build-java17-boot4`
- `postgresql`
- `quality`

Expected:

- Java 17 / Boot 3 succeeds.
- Java 17 / Boot 4 succeeds.
- PostgreSQL integration tests execute rather than silently skip.
- Quality checks succeed.

- [ ] **Step 5: Review only intended changes**

```bash
git status --short
git diff --check origin/main...HEAD
git diff --stat origin/main...HEAD
git log --oneline origin/main..HEAD
rg -n 'TO''DO|T''BD|FIX''ME|PLACE''HOLDER|<version>|X\\.Y\\.Z' .github CONTRIBUTING.md RELEASING.md docs/branching.md docs/superpowers/specs/2026-07-30-release-only-main-model-design.md docs/superpowers/plans/2026-07-30-release-only-main-model.md
```

Expected:

- No whitespace errors.
- Only governance files are changed.
- `X.Y.Z` appears only as deliberate documentation notation.
- No unresolved placeholder remains.

- [ ] **Step 6: Request a code review**

Use `superpowers:requesting-code-review` to review:

- policy correctness
- workflow trigger coverage
- exact check-name consistency
- ruleset safety
- release metadata coupling
- documentation consistency
- accidental weakening of tag, environment, secret, or publishing controls

Resolve every high-confidence issue and rerun affected checks.

---

## Task 7: Merge the final governance exception into `main`

**Remote-state task:** This task changes GitHub state. Execute only after Task 6 is green and the user has authorized execution of this implementation plan.

- [ ] **Step 1: Re-fetch and revalidate the pinned bootstrap base**

```bash
bootstrap_base_sha=cab27f008b664df78ac83247f3ad27cf160fa72e
bootstrap_state_file=/private/tmp/criteriaforge-task7-bootstrap-state
git fetch --prune origin
git status --short --branch
test "$(git rev-parse origin/main)" = "${bootstrap_base_sha}"
git rev-list --left-right --count origin/main...HEAD
git merge-base --is-ancestor origin/main HEAD
test ! -e "${bootstrap_state_file}"
```

Expected:

- Worktree is clean.
- The reviewed governance implementation contains the pinned `origin/main`.
- `origin/main` still identifies exactly `cab27f008b664df78ac83247f3ad27cf160fa72e`.
- No stale bootstrap state file exists. If one exists, inspect and resolve the prior attempt instead of overwriting it.
- If `main` moved, stop. The bootstrap exception is intentionally unusable; do not rebase, refresh, or broaden the pinned exception without a new human ruling.

- [ ] **Step 2: Prove that resetting remote `dev` is safe and record the reviewed SHA**

Capture the old remote SHA before reviewing its history. The state file contains only public commit identifiers and is retained until governance merge or rollback completes.

```bash
bootstrap_state_file=/private/tmp/criteriaforge-task7-bootstrap-state
test "$(gh pr list --state open --base dev --json number --jq 'length')" -eq 0
test "$(gh pr list --state open --head dev --json number --jq 'length')" -eq 0
verified_old_dev_sha="$(git rev-parse refs/remotes/origin/dev)"
governance_sha="$(git rev-parse HEAD)"
[[ "${verified_old_dev_sha}" =~ ^[0-9a-f]{40}$ ]]
[[ "${governance_sha}" =~ ^[0-9a-f]{40}$ ]]
git log --left-right --cherry-pick --oneline origin/main...origin/dev
git diff --stat origin/main..origin/dev
git diff --stat origin/dev..origin/main
printf 'verified_old_dev_sha=%s\ngovernance_sha=%s\n' \
  "${verified_old_dev_sha}" "${governance_sha}" > "${bootstrap_state_file}"
chmod 600 "${bootstrap_state_file}"
```

Expected:

- No pull request depends on the old remote `dev`.
- No unique intended change remains only on old remote `dev`.
- `verified_old_dev_sha` is the exact SHA whose history and tree differences were reviewed, and both that SHA and the governance SHA are persisted for lease and rollback use.
- If any condition is unclear, stop without changing remote state.

- [ ] **Step 3: Reset only remote `dev` to the verified governance tip**

Do not recapture or replace `verified_old_dev_sha`. After the fresh fetch, require remote `dev` to equal the SHA reviewed in Step 2 and repeat both PR-dependency checks before the lease-protected reset. Never push `docs/release-only-main-model` or any other migration/docs branch remotely.

```bash
bootstrap_base_sha=cab27f008b664df78ac83247f3ad27cf160fa72e
bootstrap_state_file=/private/tmp/criteriaforge-task7-bootstrap-state
source "${bootstrap_state_file}"
[[ "${verified_old_dev_sha}" =~ ^[0-9a-f]{40}$ ]]
[[ "${governance_sha}" =~ ^[0-9a-f]{40}$ ]]
git fetch --prune origin
test "$(git rev-parse origin/main)" = "${bootstrap_base_sha}"
test "$(git rev-parse refs/remotes/origin/dev)" = "${verified_old_dev_sha}"
test "$(git rev-parse HEAD)" = "${governance_sha}"
test "$(gh pr list --state open --base dev --json number --jq 'length')" -eq 0
test "$(gh pr list --state open --head dev --json number --jq 'length')" -eq 0
git push --force-with-lease="refs/heads/dev:${verified_old_dev_sha}" origin "${governance_sha}:refs/heads/dev"
git fetch --prune origin
test "$(git rev-parse origin/dev)" = "${governance_sha}"
test "$(git rev-parse origin/main)" = "${bootstrap_base_sha}"
```

Expected: only remote `dev` changes, and it identifies the exact reviewed governance commit. Any SHA mismatch, new dependent PR, or lease failure is a stop condition; do not recapture the lease SHA or retry with an unqualified force push.

**Mandatory rollback for any failure or abort after Step 3 and before governance merge:** close the governance PR if it was opened, then restore only the reviewed old `dev` SHA with a lease requiring remote `dev` to remain at the governance SHA. Do not require `main` to remain pinned in order to perform this rollback; `main` movement makes rollback more urgent because the bootstrap is no longer usable.

```bash
bootstrap_state_file=/private/tmp/criteriaforge-task7-bootstrap-state
source "${bootstrap_state_file}"
if [[ -z "${PR_NUMBER:-}" ]]; then
  rollback_pr_numbers="$(gh pr list --state open --base main --head dev \
    --json number,title \
    --jq '.[] | select(.title == "ci: adopt release-only main governance") | .number')"
  test "$(printf '%s\n' "${rollback_pr_numbers}" | sed '/^$/d' | wc -l | tr -d ' ')" -le 1
  PR_NUMBER="$(printf '%s\n' "${rollback_pr_numbers}" | sed '/^$/d')"
fi
if [[ -n "${PR_NUMBER:-}" ]]; then
  gh pr close "${PR_NUMBER}" --comment "Closing the aborted governance bootstrap before restoring dev."
fi
git fetch --prune origin
test "$(git rev-parse refs/remotes/origin/dev)" = "${governance_sha}"
test "$(gh pr list --state open --base dev --json number --jq 'length')" -eq 0
test "$(gh pr list --state open --head dev --json number --jq 'length')" -eq 0
git push --force-with-lease="refs/heads/dev:${governance_sha}" origin "${verified_old_dev_sha}:refs/heads/dev"
git fetch --prune origin
test "$(git rev-parse refs/remotes/origin/dev)" = "${verified_old_dev_sha}"
rm -f "${bootstrap_state_file}"
```

If rollback encounters a changed remote `dev`, dependent PR, or lease failure, stop and report the exact state. Never use an unqualified force push.

- [ ] **Step 4: Verify the exact live strict `protect-main` ruleset**

Automatic bootstrap expiry is trusted only when the live ruleset requires current checks against the latest `main`. Before opening the PR, require exactly one active `protect-main`, a branch target limited to `refs/heads/main`, strict required-status-check mode, and the required `pr-policy` context. Also compare all live required contexts with the checked-in source of truth.

```bash
repository=EmmanuelCazarez/criteriaforge
protect_main_ids="$(gh api --paginate "repos/${repository}/rulesets" \
  --jq '.[] | select(.name == "protect-main") | .id')"
test "$(printf '%s\n' "${protect_main_ids}" | sed '/^$/d' | wc -l | tr -d ' ')" -eq 1
protect_main_id="$(printf '%s\n' "${protect_main_ids}" | sed '/^$/d')"
protect_main_json="$(gh api "repos/${repository}/rulesets/${protect_main_id}")"
printf '%s\n' "${protect_main_json}" | jq -e '
  .name == "protect-main" and
  .target == "branch" and
  .enforcement == "active" and
  .conditions.ref_name.include == ["refs/heads/main"] and
  .conditions.ref_name.exclude == [] and
  any(.rules[];
    .type == "required_status_checks" and
    .parameters.strict_required_status_checks_policy == true and
    any(.parameters.required_status_checks[]; .context == "pr-policy"))
'
diff -u \
  <(jq -r '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context' .github/rulesets/protect-main.json | sort) \
  <(printf '%s\n' "${protect_main_json}" | jq -r '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context' | sort)
```

Expected: the exact live ruleset is active, targets only `main`, requires strict up-to-date status checks, includes `pr-policy`, and has all seven checked-in contexts. If any assertion fails, run the mandatory rollback and stop; do not open the PR.

- [ ] **Step 5: Open the final legacy governance PR**

Create a PR:

```text
base: main
head: dev (same repository)
title: ci: adopt release-only main governance
pull request base SHA: cab27f008b664df78ac83247f3ad27cf160fa72e
```

The body must clearly state:

- this is the final approved non-release exception on `main`
- existing history and signed `v0.1.0` are preserved
- the invariant starts after this squash merge
- no Maven release occurs in this PR
- the implementation plan and verification evidence

The `pr-policy` job must receive the base SHA from the pull request event. Java setup and release-metadata validation skip only this exact governance title; any wrong repository, branch, title, base, or base SHA still fails policy.

After creation, record the PR number for checks and rollback:

```bash
bootstrap_state_file=/private/tmp/criteriaforge-task7-bootstrap-state
PR_NUMBER="$(gh pr view dev --json number --jq '.number')"
[[ "${PR_NUMBER}" =~ ^[0-9]+$ ]]
printf 'PR_NUMBER=%s\n' "${PR_NUMBER}" >> "${bootstrap_state_file}"
```

- [ ] **Step 6: Wait for exact required checks**

```bash
source /private/tmp/criteriaforge-task7-bootstrap-state
gh pr checks "${PR_NUMBER}" --watch
```

Expected all pass:

- `pr-policy`
- `build-java17-boot3`
- `build-java17-boot4`
- `postgresql`
- `quality`
- `dependency-review`
- `codeql-java`

- [ ] **Step 7: Reassert the pinned base immediately before merge**

After all checks pass, re-fetch and require both current `main` and the pull request's immutable base OID to equal the pinned bootstrap SHA. Also require the PR to remain open with the exact base, head, and title. Perform the squash merge immediately after these assertions; if there is any delay or intervening action, rerun this step.

```bash
bootstrap_base_sha=cab27f008b664df78ac83247f3ad27cf160fa72e
source /private/tmp/criteriaforge-task7-bootstrap-state
git fetch --prune origin
test "$(git rev-parse refs/remotes/origin/main)" = "${bootstrap_base_sha}"
pr_json="$(gh api graphql \
  -F owner=EmmanuelCazarez \
  -F name=criteriaforge \
  -F number="${PR_NUMBER}" \
  -f query='query($owner: String!, $name: String!, $number: Int!) {
    repository(owner: $owner, name: $name) {
      pullRequest(number: $number) {
        state baseRefName baseRefOid headRefName title
        headRepository { nameWithOwner }
      }
    }
  }' \
  --jq '.data.repository.pullRequest')"
test "$(printf '%s\n' "${pr_json}" | jq -r '.state')" = OPEN
test "$(printf '%s\n' "${pr_json}" | jq -r '.baseRefName')" = main
test "$(printf '%s\n' "${pr_json}" | jq -r '.baseRefOid')" = "${bootstrap_base_sha}"
test "$(printf '%s\n' "${pr_json}" | jq -r '.headRefName')" = dev
test "$(printf '%s\n' "${pr_json}" | jq -r '.headRepository.nameWithOwner')" = EmmanuelCazarez/criteriaforge
test "$(printf '%s\n' "${pr_json}" | jq -r '.title')" = "ci: adopt release-only main governance"
```

Expected: both the fetched `main` tip and PR base OID remain exactly pinned. Any mismatch triggers the mandatory rollback; never merge a refreshed or retargeted bootstrap PR.

- [ ] **Step 8: Squash merge and verify auto-deletion**

Use the GitHub squash merge operation. Do not merge locally and do not use rebase merge.

Expected:

- `main` advances by exactly one commit.
- The PR source branch `dev` is automatically deleted.
- The resulting commit message is the governance PR title.
- The bootstrap exception is now unusable because `main` no longer has the pinned base SHA.

- [ ] **Step 9: Record the freeze baseline**

```bash
git fetch --prune origin
git rev-list --count origin/main
git log -1 --format='%H %s' origin/main
test "$(git rev-parse origin/main)" != cab27f008b664df78ac83247f3ad27cf160fa72e
git ls-remote --heads origin dev
git ls-remote --heads origin docs/release-only-main-model
git tag -v v0.1.0
rm -f /private/tmp/criteriaforge-task7-bootstrap-state
```

Expected:

- `main` now has 39 commits if no concurrent commit appeared.
- The governance commit hash becomes the invariant baseline.
- `dev` is absent remotely until Task 8 recreates it from the freeze baseline.
- No remote `docs/release-only-main-model` branch exists.
- `v0.1.0` still verifies unchanged.

If the count is not 39, stop and explain the history before proceeding; use the recorded baseline hash, not an assumed count. Continue directly to Task 8 so `dev` is recreated and protected before development resumes.

---

## Task 8: Recreate and protect `dev`

**Remote-state task:** Branch deletion/recreation is approved by the design but remains gated by fresh evidence. `protect-dev` remains fully active with no bypass during normal development; this task temporarily disables only that exact ruleset for the shortest possible SHA-validated reset window.

- [ ] **Step 1: Prove branch recreation is safe immediately before deletion**

```bash
git fetch --prune origin
gh pr list --state open --base dev --json number,title,headRefName,url
gh pr list --state open --head dev --json number,title,baseRefName,url
git log --left-right --cherry-pick --oneline origin/main...origin/dev
git diff --stat origin/main..origin/dev
```

Expected:

- No PR depends on the old `dev`.
- No unique intended change remains only on old `dev`.
- If either condition fails, stop.

- [ ] **Step 2: Create and verify the live `protect-dev` ruleset**

Query existing repository rulesets by exact name. If `protect-dev` does not exist, create it from `.github/rulesets/protect-dev.json`; if it exists, update that exact ID only after comparing the live configuration with the checked-in source. Re-query and verify active enforcement against only `refs/heads/dev`. Never create a duplicate ruleset.

Expected:

- the exact ruleset exists and is active before the reset window
- direct pushes, deletion, non-fast-forward updates, and non-PR changes are blocked
- the seven required contexts and squash-only merge policy match the checked-in source
- required approval count is zero because the live collaborator audit found one eligible maintainer

- [ ] **Step 3: Temporarily disable only the exact `protect-dev` ruleset**

Query the ruleset by name, capture its ID and current full payload, and verify it targets only `refs/heads/dev`. Update only `enforcement` from `active` to `disabled`. Re-query until the exact ruleset reports `disabled`.

Do not disable `protect-main`, release-tag protection, secret scanning, or any repository-wide security setting. If any subsequent reset command fails, reactivate `protect-dev` before stopping.

- [ ] **Step 4: Delete and recreate remote `dev` at the freeze baseline**

Perform explicit remote operations:

```bash
git push origin --delete dev
git push origin origin/main:refs/heads/dev
git fetch --prune origin
test "$(git rev-parse origin/main)" = "$(git rev-parse origin/dev)"
```

Expected: `origin/main` and `origin/dev` identify the same freeze-baseline commit.

- [ ] **Step 5: Reactivate and verify `protect-dev` immediately**

Restore the captured ruleset payload with `enforcement: active`, then query it again.

Expected:

- `protect-dev` is active before any new PR is accepted
- deletion and non-fast-forward protection are restored
- required PR, linear history, squash-only, conversation resolution, and all exact checks are active
- no bypass actor was added

- [ ] **Step 6: Realign the local `dev` checkout without rewriting unrelated work**

In the checkout where `dev` is currently active:

```bash
git status --short
git switch --detach origin/main
```

From a worktree where `dev` is not checked out:

```bash
git branch -f dev origin/dev
```

Then in the original checkout:

```bash
git switch dev
git status --short --branch
```

Expected: clean local `dev` tracks recreated `origin/dev`. Do not proceed if the original checkout is dirty.

- [ ] **Step 7: Confirm the live `protect-dev` ruleset matches the repository source**

Use the GitHub API with `.github/rulesets/protect-dev.json`. The ruleset should already have been created from this source before the reset; query by exact name to prevent duplicates and update that exact ID only if configuration drift exists.

Expected:

- active `protect-dev` targets only `refs/heads/dev`
- required PR, linear history, squash-only, conversation resolution, and exact checks are active
- deletion and non-fast-forward updates are blocked
- future deletion/recreation requires the same temporary-disable, exact-SHA, immediate-reactivation procedure

- [ ] **Step 8: Audit live repository merge settings**

Verify:

- default branch is `main`
- squash merge enabled
- merge commits disabled
- rebase merges disabled
- delete branch on merge enabled

Do not change vulnerability-alert, automated-security-fix, secret-scanning, push-protection, protected-environment, or release-tag settings except to restore the already approved state if drift is detected and explicitly authorized.

---

## Task 9: Prove the new policy with disposable PRs

**Remote-state task:** Every temporary branch opened here must be closed or merged and confirmed deleted before completion.

- [ ] **Step 1: Prove normal work targets `dev`**

Create `ci/dev-protection-smoke-test` from recreated `dev` and make one harmless, reviewable process-documentation assertion if needed.

Open a PR to `dev` with a valid Conventional Commit title.

Expected:

- direct push is not used
- all exact checks run
- squash is the only merge option
- required pull-request and conversation-resolution rules apply

- [ ] **Step 2: Squash merge the `dev` smoke PR**

After checks and maintainer review, squash merge.

Expected:

- `dev` advances by exactly one commit
- the source branch is auto-deleted
- `main` remains at the freeze baseline

- [ ] **Step 3: Prove non-`dev` sources cannot update `main`**

Create `ci/main-source-policy-smoke-test` from current `dev`, push it, and open a PR to `main` with a non-release title.

Expected:

- `pr-policy` fails because the head branch is not exact `dev`
- the PR cannot merge
- no other protection is weakened

- [ ] **Step 4: Close and delete the negative-test branch**

Close the failed PR and delete `ci/main-source-policy-smoke-test`.

Verify:

```bash
git fetch --prune origin
git ls-remote --heads origin ci/dev-protection-smoke-test ci/main-source-policy-smoke-test
```

Expected: neither disposable branch exists.

---

## Task 10: Resolve existing Dependabot and feature branches

- [ ] **Step 1: Close the superseded Dependabot PR**

Verify that the final governance commit already contains the exact dependency-review action upgrade from the open `main`-targeting Dependabot PR.

Compare:

```bash
gh pr diff 17
git show origin/main:.github/workflows/ci.yml | rg "dependency-review-action"
```

Expected: `origin/main` contains `actions/dependency-review-action@v5`.

Close PR `#17` as superseded by the governance migration, and delete its branch:

```text
dependabot/github_actions/main/actions/dependency-review-action-5
```

Do not merge that PR into `main`.

- [ ] **Step 2: Verify future Dependabot target policy**

Confirm live default-branch configuration contains `target-branch: dev` for Maven and GitHub Actions ecosystems.

Explain in repository docs or the closing comment:

- Dependabot creates and maintains each update branch.
- GitHub rebases/recreates it when needed.
- Maintainers review the PR; they do not keep the bot branch aligned manually.
- The branch is deleted after squash merge or PR closure.

- [ ] **Step 3: Capture the old feature patch**

Before deleting anything:

```bash
git fetch origin feature/criteriaforge-library
git log --oneline origin/main..origin/feature/criteriaforge-library
git diff --binary 476da0b..origin/feature/criteriaforge-library -- \
  criteriaforge-core/src/main/java
```

Expected: one behavior-neutral refactor commit affecting the five known record compact constructors.

- [ ] **Step 4: Recreate the refactor from current `dev`**

Create:

```text
refactor/remove-redundant-record-assignments
```

from current `dev`.

Apply only the intended changes to:

- `Condition.java`
- `Negation.java`
- `QueryPolicyRegistration.java`
- `QueryRequest.java`
- `Sorting.java`

Preserve validation calls and public behavior. Do not add unrelated formatting.

- [ ] **Step 5: Verify behavior and patch parity**

Run focused core tests first, then:

```bash
./mvnw -B -ntp clean verify
git diff --check dev...HEAD
git diff --stat dev...HEAD
```

Compare the semantic patch to the old feature branch. Expected:

- the same redundant assignments are removed
- null validation remains
- no API signature changes
- full build passes

- [ ] **Step 6: Squash merge the refactor into `dev`**

Open a PR to `dev` with a valid title such as:

```text
refactor: remove redundant record assignments
```

Require all checks and complete maintainer review; squash merge; verify automatic deletion of the replacement branch.

- [ ] **Step 7: Delete the obsolete feature branch**

Only after the replacement squash is present on `dev` and parity has been proven, delete:

```text
feature/criteriaforge-library
```

Verify both old and replacement feature branches are absent remotely.

---

## Task 11: Run the final branch, release, and artifact audit

- [ ] **Step 1: Audit remote branches and open PRs**

```bash
git fetch --prune origin
git branch -r
gh pr list --state open --json number,title,headRefName,baseRefName,url
```

Expected:

- permanent branches are `main` and `dev`
- only branches for genuinely open work exist
- no obsolete Dependabot, old feature, migration, or smoke-test branch remains

- [ ] **Step 2: Audit the release-ledger invariant**

```bash
git log --first-parent --oneline origin/main
git log --oneline <FREEZE_BASELINE>..origin/main
git log --oneline <FREEZE_BASELINE>..origin/dev
```

Expected:

- no commit appears after the freeze baseline on `main` unless it is an official release commit
- smoke, refactor, next-snapshot, and Dependabot integration commits exist only on `dev`

- [ ] **Step 3: Audit live rules and repository settings**

Query live repository settings and rulesets. Compare live payloads with:

- `.github/rulesets/protect-main.json`
- `.github/rulesets/protect-dev.json`
- `.github/rulesets/restrict-release-tag-creation.json`
- `.github/rulesets/protect-release-tags.json`
- `.github/repository-settings.json`

Expected:

- only `dev` can satisfy the `main` source policy
- approved short-lived/fork PRs can target `dev`
- exact checks are required
- squash-only and auto-delete are active
- signed `v*` tag controls remain active
- merging `dev` to `main` alone does not publish; the Release workflow starts only from a pushed signed version tag or manual rerun of that existing signed tag

- [ ] **Step 4: Reverify published and unpublished artifacts**

Check public resolution:

```text
io.github.emmanuelcazarez:criteriaforge-spring-boot-starter:0.1.0 -> HTTP 200
io.github.emmanuelcazarez:criteriaforge-example:0.1.0 -> HTTP 404
```

Expected:

- the starter remains publicly resolvable from Maven Central
- the runnable example remains GitHub-only by design

- [ ] **Step 5: Reverify signed release provenance**

```bash
git tag -v v0.1.0
git show --no-patch --format=fuller v0.1.0
```

Expected:

- the signature verifies with the existing release fingerprint
- target commit is unchanged

- [ ] **Step 6: Report the migration result precisely**

The completion report must include:

- freeze-baseline commit and actual `main` commit count
- retained legacy history and signed tag
- the capability-weighted prior-work retention audit (approximately 78 percent), distinguishing retained release safeguards from superseded trunk-only guidance
- live `main` and `dev` policy summary
- exact CI pipelines and trigger branches
- resolved PR/branch list
- confirmation that no release was published during migration
- confirmation that no credential value was read or exposed
- any remaining active work branch, if one legitimately exists

Do not claim historical `main` was reduced to one commit. State that the invariant applies prospectively from the freeze baseline.

---

## Post-Migration Release Runbook

For every future release:

- [ ] Integrate all work into `dev` via reviewed squash PRs.
- [ ] Merge `release/X.Y.Z` into `dev` after stable version and changelog validation.
- [ ] Open exact `dev` to `main` PR titled `chore(release): release X.Y.Z`.
- [ ] Require all seven checks; while the repository has one eligible maintainer, rely on mandatory PR visibility, exact source policy, checks, and resolved conversations rather than an impossible self-approval.
- [ ] Squash merge once.
- [ ] Wait for post-merge CI and CodeQL on the exact `main` release commit.
- [ ] Create and locally verify a signed `vX.Y.Z` tag on that exact commit, then push it.
- [ ] Verify the tag-push-triggered release workflow validates the existing signed tag and uploads a signed bundle containing only intended Maven Central modules; complete the separate manual Central publication approval before public verification.
- [ ] Verify artifacts from a clean public repository context.
- [ ] Delete and recreate `dev` at the new `main`.
- [ ] Add the next snapshot to `dev` through a short-lived squash PR.
- [ ] Verify all temporary branches are deleted.
