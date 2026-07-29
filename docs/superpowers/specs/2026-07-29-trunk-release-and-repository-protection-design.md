# CriteriaForge Trunk, Release, and Repository Protection Design

**Status:** Approved
**Date:** 2026-07-29

## Context

CriteriaForge currently uses long-lived `dev` and `main` branches. CI runs on
both branches, Dependabot targets `dev`, and Maven Central publication is
triggered by either a published GitHub Release or a manual workflow dispatch.
The existing repository permits merge commits, squash merges, and rebase
merges, and it does not enforce the documented branch policy through active
rulesets.

This design replaces that model with trunk-based development. `main` becomes
the only permanent branch and remains releasable at all times. Pull requests
carry review and development detail, while squash merges keep the permanent
commit history concise. Signed immutable tags identify official releases.

## Goals

- Use a conventional and contributor-friendly open-source branch model.
- Record exactly one logical commit on `main` for every merged pull request.
- Delete temporary same-repository branches after successful merges.
- Require the complete Java, Spring Boot, PostgreSQL, and quality matrix before
  merging or publishing.
- Keep Maven Central publication explicit and protected by human approval.
- Prevent direct pushes, force pushes, deletion, and unreviewed changes to
  `main`.
- Protect release tags from replacement or deletion.
- Preserve the already published `v0.1.0` tag and Maven coordinates unchanged.

## Non-goals

- Rewriting or moving the signed `v0.1.0` tag.
- Rewriting the existing `main` history. The new policy governs commits created
  after the trunk migration.
- Automatically publishing Central deployments without maintainer inspection.
- Automatically merging Dependabot or security pull requests.
- Requiring external contributors to use repository-owned branch names.
- Introducing a merge queue before repository activity requires one.

## Branch model

`main` is the default and only permanent branch:

```text
temporary branch or fork
        |
        v
pull request targeting main
        |
        v
required policy and verification checks
        |
        v
squash merge -> one commit on main
        |
        v
delete same-repository source branch
```

Repository-owned temporary branches use one of these prefixes:

- `feature/`
- `fix/`
- `docs/`
- `refactor/`
- `test/`
- `build/`
- `ci/`
- `chore/`
- `release/`
- `dependabot/`

The policy workflow enforces these prefixes only when the pull request source
belongs to the CriteriaForge repository. Fork owners control their own branch
names, so external pull requests are not rejected based on head-branch naming.

Temporary branches may contain any number of development or fixup commits.
Squash merging is the mechanism that guarantees one logical commit reaches
`main`.

## Repository merge settings

The repository permits only squash merging:

- allow squash merges;
- disable merge commits;
- disable rebase merges;
- use the pull request title as the squash commit title;
- use the pull request description as the squash commit body;
- automatically delete merged head branches;
- keep automatic merging disabled;
- do not configure a merge queue.

Automatic deletion applies only to same-repository branches and cannot delete a
branch in a contributor's fork. `main` is protected from deletion. Temporary
branches must not receive protection rules that prevent automatic cleanup.

## Commit and pull request conventions

Pull request titles become permanent commit titles and therefore follow these
Conventional Commit categories:

- `feat`
- `fix`
- `docs`
- `refactor`
- `test`
- `build`
- `ci`
- `chore`
- `perf`
- `revert`

Scopes are optional. Examples:

```text
feat(jpa): add grouped projections
fix(web): preserve repeated query parameters
docs: explain projection aliases
chore(release): prepare 0.2.0
```

The `pr-policy` check validates the title and, for same-repository pull
requests, the source-branch prefix. It does not require the source branch
itself to contain only one commit.

## Main ruleset

An active repository ruleset named `protect-main` targets only `main` and
enforces:

- pull requests are required;
- only squash merging is allowed;
- linear history is required;
- direct pushes are blocked;
- force pushes are blocked;
- branch deletion is blocked;
- review conversations must be resolved;
- the pull request must be up to date with `main`;
- administrators follow the rules during normal operation;
- these status checks are required:
  - `pr-policy`;
  - `build-java17-boot3`;
  - `build-java17-boot4`;
  - `postgresql`;
  - `quality`;
  - `dependency-review`;
  - the configured Java CodeQL check.

The ruleset initially requires zero approving reviews because the repository
has one maintainer and GitHub does not allow pull request authors to approve
their own changes. Once a second maintainer is available, the ruleset changes
to require one approval, dismiss stale approvals, require approval of the most
recent reviewable push, and use `CODEOWNERS` for sensitive release and build
files.

Required signed commits are not enabled on `main`. That rule can prevent a
maintainer from squash-merging a pull request authored by an external
contributor. Release authenticity is enforced through signed release tags
instead.

No routine bypass actor is configured. Emergency recovery requires an explicit
temporary administrative ruleset change, followed by restoration and an
auditable pull request.

## Continuous integration

The `CI` workflow listens to:

- pull requests targeting `main`;
- pushes to `main`, including squash merges;
- manual workflow dispatch for diagnostics.

It no longer listens to `dev`. There are no path filters: every accepted change
can affect a multi-module Java build and therefore runs the complete matrix.

The workflow runs these independent jobs:

1. `build-java17-boot3`
   - Temurin Java 17
   - Spring Boot 3 baseline
   - Maven `verify`
2. `build-java17-boot4`
   - Temurin Java 17
   - Spring Boot 4 compatibility
   - Maven `verify`
3. `postgresql`
   - PostgreSQL Testcontainers profile
   - Maven `verify`
4. `quality`
   - Checkstyle, JaCoCo, documentation, and configured quality gates
5. `pr-policy`
   - pull request title
   - same-repository branch prefix
6. `dependency-review`
   - rejects newly introduced vulnerable dependencies at the configured
     severity threshold

CodeQL runs for Java pull requests and `main` pushes and is required by the
ruleset after its first successful check exists. Existing CI concurrency
continues to cancel superseded runs for the same pull request or branch.

## Versioning policy

CriteriaForge follows Semantic Versioning while it remains in initial
development:

- `0.1.1` is used for backward-compatible fixes after `0.1.0`;
- `0.2.0` is used when the release adds backward-compatible features;
- breaking changes during `0.y.z` require a new `0.MINOR.0` release and explicit
  migration notes;
- `1.0.0` declares the public API stable;
- after `1.0.0`, incompatible API changes increment the major version,
  backward-compatible functionality increments the minor version, and
  backward-compatible fixes increment the patch version.

If the next planned release contains only fixes, development remains
`0.1.1-SNAPSHOT`. If features enter the release scope, development changes to
`0.2.0-SNAPSHOT`.

Published coordinates are immutable and are never replaced.

## Release preparation

A release is prepared on a temporary branch such as `release/0.2.0`. Its pull
request:

- changes every reactor module from the current snapshot to the exact release
  version;
- updates the changelog and release notes;
- contains no unrelated feature work;
- uses a title such as `chore(release): prepare 0.2.0`;
- passes the complete required matrix;
- is squash-merged into `main`;
- is automatically deleted after merging.

The post-merge CI run on the exact `main` commit must succeed before a tag is
created.

## Release tags

Two active tag rulesets target `v*` because GitHub bypass actors apply to an
entire ruleset rather than to one rule inside it:

- `restrict-release-tag-creation` restricts creation to repository
  administrators;
- `protect-release-tags` has no bypass actor and blocks tag updates and
  deletion;
- together they preserve `v0.1.0` unchanged.

Release tags are annotated and signed. The tag name uses `vX.Y.Z`, while the
Maven version uses `X.Y.Z`.

GitHub tag rules cannot verify the expected GPG fingerprint before accepting a
tag. The Release workflow therefore verifies:

- the tag is annotated;
- the signature is valid;
- the signing fingerprint matches the pinned maintainer fingerprint;
- the name is a valid release tag;
- the tagged commit is reachable from `main`;
- the Maven version exactly matches the tag;
- the Maven version is not a snapshot.

The maintainer's armored public signing key is committed at
`.github/release-signing-key.asc`. The workflow imports only that public key
for tag verification and compares the resulting fingerprint with the expected
fingerprint stored in workflow configuration. The private key remains only in
the protected environment.

Publishing credentials are not accessible until these checks and the complete
release verification matrix succeed.

## Release workflow

Pushing a signed `v*` tag is the normal release trigger. A manual
`workflow_dispatch` with a version remains available only to recover an
existing signed tag.

The workflow separates verification from privileged publication:

1. `candidate`
   - resolves the existing tag;
   - validates tag format, signature, fingerprint, version, and `main`
     ancestry;
   - uses no publication secrets.
2. `build-java17-boot3`
3. `build-java17-boot4`
4. `postgresql`
5. `quality`
   - all run against the exact tagged source;
   - all must succeed.
6. `publish`
   - depends on every candidate and verification job;
   - uses the protected `maven-central` environment;
   - waits for required maintainer approval;
   - imports signing material into an ephemeral keyring;
   - signs and uploads the Central bundle;
   - excludes `criteriaforge-example`;
   - waits for Central status `VALIDATED`;
   - retains `autoPublish=false`;
   - removes ephemeral signing material even after failure.

After validation, the maintainer inspects and manually publishes the deployment
in the Maven Central Portal. The corresponding GitHub Release is published only
after Central publication succeeds. This avoids advertising a release whose
artifacts are not yet available.

## Post-release development

After publishing, a temporary branch opens a small pull request that advances
the project to the next planned snapshot, for example:

```text
chore: start 0.2.1-SNAPSHOT development
```

The pull request passes required checks, is squash-merged, and its source branch
is deleted automatically.

## Dependency and security automation

Dependabot configuration moves both Maven and GitHub Actions updates to
`main`. Version-update pull requests remain weekly with the configured open-PR
limits. Spring Boot semantic-major updates remain ignored while Spring Boot 3
is the supported baseline and Spring Boot 4 is a compatibility target.

The repository keeps:

- dependency vulnerability alerts enabled;
- automatic Dependabot security pull requests disabled;
- Dependabot auto-merge disabled;
- secret scanning enabled;
- push protection enabled;
- CodeQL enabled for Java;
- dependency review required for pull requests.

Maven Central credentials and the private signing key remain exclusively in
the protected `maven-central` environment. No credential values may appear in
repository files, logs, issues, pull requests, or release notes.

## Migration from the current model

Migration is performed without changing published release identity:

1. Fetch and compare live `main`, `dev`, tags, open pull requests, and workflow
   state.
2. Preserve the signed `v0.1.0` tag and published Maven artifacts unchanged.
3. Identify every tree change present on `dev` but missing from `main`.
4. Create one reviewed migration pull request targeting `main` that contains
   only the intended unreleased changes.
5. Run the complete verification and security matrix.
6. Squash-merge the migration pull request.
7. Update CI, release, Dependabot, branching documentation, and contribution
   documentation for trunk-based development through reviewed pull requests.
8. Create required workflows and allow each future required status name to
   report successfully before activating the corresponding ruleset requirement.
9. Configure squash-only merging and automatic source-branch deletion.
10. Activate `protect-main`, `restrict-release-tag-creation`, and
    `protect-release-tags`.
11. Confirm no unique or open work depends on `dev`.
12. Delete the remote `dev` branch and obsolete same-repository temporary
    branches.
13. Verify the default branch, rulesets, Actions triggers, Dependabot target,
    environment protection, tags, and Maven Central coordinates.

The migration must compare trees rather than relying only on ahead/behind
counts because the existing branches contain merge commits and divergent
history.

## Failure and recovery behavior

- **Required check fails:** merging remains blocked; fix the same pull request
  branch and rerun checks.
- **Policy check fails:** correct the PR title or same-repository branch and
  rerun without bypass.
- **Unsigned or unexpected tag:** Release stops before environment approval or
  credential access. Deleting an invalid unpublished tag requires an explicit
  temporary administrative change to the immutable-tag ruleset followed by
  immediate restoration; an existing published tag is never moved or deleted.
- **Release verification fails:** no Central upload occurs.
- **Central validation fails:** drop the deployment, correct the source through
  a new pull request, and use a new release version when required.
- **Central publication succeeds but GitHub Release is absent:** publish the
  GitHub Release from the unchanged signed tag.
- **Automatic branch deletion is blocked:** inspect open pull requests and
  protection rules, then delete only the resolved temporary branch.
- **Ruleset causes lockout:** temporarily adjust the ruleset as administrator,
  correct the configuration through a pull request where possible, and restore
  full enforcement.
- **Signing key exposure:** revoke the key, rotate environment secrets, publish
  the revocation, and require a new trusted signing fingerprint before another
  release.

## Acceptance criteria

The design is implemented when:

- `main` is the only permanent branch and the default branch;
- all pull requests merge through squash only;
- one logical commit appears on `main` per pull request merged after the trunk
  migration;
- same-repository source branches are automatically deleted;
- `dev` no longer exists remotely;
- CI runs only for `main` pull requests, `main` pushes, and manual diagnostics;
- all specified checks are required and passing on a representative pull
  request;
- Dependabot targets `main`;
- direct pushes, force pushes, and deletion of `main` are blocked;
- `v*` tags cannot be updated or deleted;
- an invalid or unsigned release tag cannot access publishing credentials;
- the exact tagged source runs the complete release matrix;
- Central publication still requires protected-environment approval and manual
  Portal publication;
- `criteriaforge-example` remains absent from Maven Central;
- `v0.1.0` and its published coordinates remain unchanged.
