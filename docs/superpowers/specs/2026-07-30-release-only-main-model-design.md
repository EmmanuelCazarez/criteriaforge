# Release-only `main` branch model

**Date:** 2026-07-30
**Status:** Approved direction; implementation pending

## Objective

CriteriaForge will use `main` as an official release ledger and `dev` as the
permanent integration branch. Temporary development and automation branches
merge into `dev`; only a release pull request from `dev` may merge into
`main`.

The repository already has 38 commits on `main`, including the immutable
signed `v0.1.0` release and the trunk-protection migration. Those commits are
preserved as legacy history. Rewriting them would disconnect the published
signed release tag from the branch that represents releases. One final
governance correction may be squash-merged to install this model. After that
correction, every new `main` commit must represent exactly one official
release.

That correction uses a one-time bootstrap exception: a same-repository
`dev`-to-`main` pull request titled exactly
`ci: adopt release-only main governance` is accepted only while the pull
request base SHA is `cab27f008b664df78ac83247f3ad27cf160fa72e`. Once `main`
moves, the exception becomes unusable without any follow-up configuration.
Task 7 verifies that the exact live `protect-main` ruleset is active,
main-only, and requires strict current status checks including `pr-policy`.
Immediately before merge it also requires both fetched `main` and the PR base
OID to remain at the pinned SHA.

## Invariants

1. The signed `v0.1.0` tag remains unchanged at
   `e35eaeed65ece159e4f429818f6264a5a0a306ae`.
2. `main` remains the default branch and release ledger.
3. `dev` is the permanent integration branch between releases.
4. Same-repository development, maintenance, documentation, release-prep,
   and Dependabot branches are temporary.
5. Temporary branches merge into `dev` by squash and are deleted.
6. Only `dev` may be the source of a pull request to `main`.
7. A pull request from `dev` to `main` represents one official release and is
   squash-merged as one commit.
8. After a release merge, `dev` is deleted or reset and recreated from the
   new `main` commit before development resumes.
9. Release tags are annotated, signed by the pinned maintainer key, created
   only by a repository administrator, and immutable after creation.
10. A merge to either branch never publishes to Maven Central. Publication
    starts only from a verified signed `vX.Y.Z` tag on `main`.

## Branch flow

```text
feature/fix/docs/refactor/test/build/ci/chore/dependabot branch
                              |
                              | pull request + required checks + squash
                              v
                             dev
                              |
                              | release preparation complete
                              | pull request + required checks + squash
                              v
                  main: one official release commit
                              |
                              | signed vX.Y.Z tag
                              v
              protected Maven Central release workflow
```

External contributors may use arbitrary branch names in forks, but their pull
requests target `dev`. Same-repository branches must use an approved temporary
prefix.

## Pull request policies

### Pull requests to `dev`

- Same-repository sources must use `feature/`, `fix/`, `docs/`, `refactor/`,
  `test/`, `build/`, `ci/`, `chore/`, `release/`, or `dependabot/`.
- Pull request titles use an approved Conventional Commit type.
- All required CI and CodeQL checks pass against current `dev`.
- Only squash merge is available.
- The source branch is deleted after merge.

### Pull requests to `main`

- The source repository must be CriteriaForge itself.
- The source branch must be exactly `dev`.
- The title must be `chore(release): release X.Y.Z` with a valid semantic
  version and no leading zeroes.
- The Maven reactor version and changelog release must match `X.Y.Z`.
- All required CI and CodeQL checks pass against current `main`.
- The pull request is squash-merged, creating one official release commit.
- No feature, fix, documentation, Dependabot, or hotfix branch may target
  `main` directly. Urgent work still flows through `dev`.

The one-time governance bootstrap is the sole exception to the release-title
and release-metadata requirements. It passes `pr-policy` only when the base is
`main`, the source repository is CriteriaForge itself, the source branch is
exactly `dev`, the title is exactly
`ci: adopt release-only main governance`, and the pull request base SHA is
`cab27f008b664df78ac83247f3ad27cf160fa72e`. CI skips Java setup and release
metadata validation only for that exact title; the policy validator still
rejects the title unless every bootstrap field matches. Normal release pull
requests retain the exact `chore(release): release X.Y.Z` title and stable
Maven/changelog metadata checks.

## Post-release `dev` lifecycle

A squash merge does not make the resulting `main` commit an ancestor of the
old `dev` history. Carrying that divergent `dev` branch forward would retain
duplicate development history and complicate future releases.

Therefore, after each successful release merge:

1. verify that the `main` tree equals the released `dev` tree;
2. verify post-merge CI and CodeQL on `main`;
3. delete the old `dev` branch;
4. recreate `dev` at the new `main` commit;
5. verify `dev` protection before accepting new pull requests.

The `dev` ruleset allows controlled deletion/recreation for this lifecycle but
blocks force pushes and direct updates during normal development.

## Dependabot

Dependabot reads `.github/dependabot.yml` from the default branch, `main`, but
all generated version-update pull requests target `dev`.

Dependabot owns each temporary branch. Maintainers do not manually keep those
branches aligned. Dependabot rebases or recreates them when necessary. After a
green squash merge to `dev`, GitHub deletes the Dependabot branch.

The currently open dependency-review-action v5 pull request is retargeted or
recreated against the newly aligned `dev`; it must not merge directly into
`main`.

## Existing public-API refactor branch

`feature/criteriaforge-library` at `cfca670` contains one behavior-neutral
record-constructor cleanup across five core files. The old branch is not
merged directly because it predates the new integration baseline.

The patch is recreated on an approved `refactor/` branch from current `dev`,
verified with the public-API and full Java test suites, squash-merged into
`dev`, and deleted. The obsolete `feature/criteriaforge-library` branch is
then deleted after the replacement PR is proven to contain the same intended
changes.

## CI and repository protection

The existing compatibility and quality work remains:

- Java 17 with Spring Boot 3 baseline;
- Java 17 with Spring Boot 4 compatibility;
- PostgreSQL Testcontainers integration;
- quality and documentation checks;
- dependency review;
- CodeQL Java analysis.

CI runs for pull requests targeting `dev` or `main`, and for pushes to both
permanent branches. Pull request title edits rerun CI so title policy cannot
retain stale approval. The existing exact check names remain stable.

`protect-main` continues to block direct pushes, deletion, force pushes, and
non-squash merges. Its PR policy is supplemented by the source-branch and
release-version workflow check. The policy receives the pull request base SHA
from GitHub and pins the one-time governance bootstrap to the pre-migration
`main` commit, so the exception expires automatically when that branch
advances.
The migration does not assume that checked-in protection is live: before
opening the bootstrap PR it queries the exact live ruleset and proves active
enforcement, a main-only target, strict required-status-check mode, the
`pr-policy` context, and parity with all checked-in required contexts.

A new `protect-dev` ruleset requires current pull requests, linear history,
resolved review conversations, and the required checks. It blocks direct and
force-pushed updates while permitting the documented post-release branch
recreation procedure.

Repository-wide squash-only merge settings, automatic source-branch deletion,
secret scanning, push protection, vulnerability alerts, and disabled automatic
security PRs remain unchanged.

## Release workflow

The signed-tag release workflow, pinned public key, historical-candidate
validator, Maven Central protected environment, and immutable `v*` tag rules
remain unchanged.

Release preparation occurs on a temporary `release/X.Y.Z` branch targeting
`dev`. Once merged and verified on `dev`, the release PR from `dev` to `main`
creates the single official release commit. A maintainer then creates the
signed `vX.Y.Z` tag on that exact `main` commit.

## Migration sequence

1. Preserve the current `main` history and signed `v0.1.0` tag.
2. Prepare one final governance correction PR containing branch policies,
   CI triggers, Dependabot target, ruleset source of truth, and documentation.
3. After freshly proving that remote `dev` has no unique intended work or
   dependent pull request, record that reviewed SHA. Re-fetch, require remote
   `dev` still equals the recorded SHA, repeat PR-dependency checks, and reset
   it with an exact force-with-lease to the verified governance tip; never
   publish a remote migration/docs branch.
4. Retain both old and governance SHAs until merge. Any failure before merge
   closes the bootstrap PR and restores the reviewed old `dev` with a lease
   requiring remote `dev` still equal the governance SHA.
5. Verify live strict `protect-main`, open the pinned one-time `dev`-to-`main`
   governance PR, and immediately before squash merge reassert both current
   `main` and the PR base OID equal the pinned SHA.
6. Squash-merge the PR as the final legacy administrative commit.
7. Recreate `dev` from the corrected `main` tip.
8. Apply and verify live `protect-main` and `protect-dev` rulesets.
9. Retarget or recreate Dependabot PRs against `dev`.
10. Recreate the five-file record cleanup on a `refactor/` branch from `dev`,
   verify it, squash-merge it to `dev`, and delete both temporary source
   branches when safe.
11. Verify the final remote inventory: `main`, `dev`, and branches belonging
   only to active pull requests.

## Retention audit of the prior migration

Approximately 78% of the functional work remains useful:

- all Maven release integrity and artifact-publication safeguards remain;
- all POM and published-example exclusions remain;
- CI jobs remain, with branch triggers adapted;
- repository security, squash settings, and tag protection remain;
- `protect-main` remains but gains release-only source validation;
- trunk-specific PR policy and branch documentation are replaced;
- the previous trunk design and execution plan remain historical evidence but
  are marked superseded by this specification.

This estimate is capability-weighted. A raw line count understates retention
because the prior trunk spec and plan account for 2,104 of 2,905 added lines.

## Success criteria

- `main` receives no non-release commit after the governance correction.
- Every future `main` commit is one squash-merged official release.
- `dev` contains integration commits and is recreated after every release.
- Temporary and Dependabot branches are automatically removed after merge.
- The existing signed `v0.1.0` tag and Maven Central artifacts remain valid.
- Release publication remains gated, signed, reproducible, and manually
  approved.
