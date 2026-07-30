# Branch and release workflow

`main` is the default branch and official release ledger. `dev` is the integration branch. The only pull request with `main` as its destination is exact `dev` for a release; approved temporary branches and external forks target `dev`.

```mermaid
flowchart TD
    A["Approved temporary branch or external fork"] --> B["Pull request to dev"]
    B --> C["Required checks and review"]
    C --> D["Squash merge into dev"]
    D --> E["Integrated dev"]
    E --> F["release/X.Y.Z from dev"]
    F --> G["Release preparation PR to dev"]
    G --> H["Squash merge; delete release branch"]
    H --> I["dev to main PR: chore(release): release X.Y.Z"]
    I --> J["Squash merge: sole release commit on main"]
    J --> K["Post-merge CI and CodeQL"]
    K --> L["Maintainer creates signed vX.Y.Z tag"]
    L --> M["Tag push triggers Release and protected upload"]
    M --> N["Manual Maven Central publication"]
    N --> O["Controlled delete and recreate dev from main"]
    O --> P["chore/next-snapshot PR to dev"]
    P --> D
```

## Temporary branches and pull requests

Contributors branch from current `dev`. Same-repository temporary branches must use one of these prefixes:

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

External fork branch names are accepted. The prefix policy applies only to branches owned by the CriteriaForge repository. All temporary branches and forks open pull requests to `dev`; `dev` alone opens the release pull request to `main`.

Temporary branches may contain multiple development or fixup commits. GitHub writes one squash commit to `dev` for each merged pull request. Pull request titles become that commit title and must use an approved Conventional Commit type: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`, or `revert`. Scopes are optional.

Only squash merging is enabled. Resolve review conversations and update the pull request with current destination branch before merging. Same-repository short-lived source branches are automatically deleted after merge; GitHub cannot delete a branch in a contributor's fork.

The `dev` to `main` release pull request is the sole exception to the general title rule: it must originate from exact `dev` and must have the exact title `chore(release): release X.Y.Z`. It must also carry exactly matching stable Maven version `X.Y.Z` and a dated `CHANGELOG.md` heading `## [X.Y.Z] - YYYY-MM-DD`.

## Required checks and protection

The permanent branch rulesets protect `dev` and `main` against direct pushes, force pushes, and deletion; require linear history, pull requests current with the destination branch, resolved review conversations, and squash-only merges. They require these exact checks:

- `pr-policy`
- `build-java17-boot3`
- `build-java17-boot4`
- `postgresql`
- `quality`
- `dependency-review`
- `codeql-java`

No approving review is required while only one eligible maintainer exists. Every release still requires complete maintainer review. When a second eligible maintainer exists, maintainers increase the required approval count for both permanent-branch rulesets to one.

## Release lifecycle

1. Confirm `dev` is green and all intended work is integrated.
2. Create `release/X.Y.Z` from `dev`, set the reactor to stable `X.Y.Z`, and update the dated changelog.
3. Squash-merge `release/X.Y.Z` into `dev`; the release branch is deleted.
4. Open exact `dev` to `main` with exact title `chore(release): release X.Y.Z`; all checks and complete maintainer review are required.
5. Squash-merge once, creating the sole release commit on `main`, then wait for post-merge CI and CodeQL on that exact commit.
6. A maintainer creates and locally verifies signed tag `vX.Y.Z` on that exact commit, then pushes it. The tag-push-triggered `Release` workflow validates the signed candidate and uploads the intended artifacts through protected `maven-central`; a `main` merge alone does not publish.
7. The Central publication remains a separate manual approval after validation. `workflow_dispatch` may rerun an existing signed tag but never replaces it.
8. In the controlled post-release operation, delete `dev` and recreate it from the new `main` tip. A short-lived `chore/next-snapshot` branch advances the next snapshot and squash-merges back into `dev`.

Use `0.2.0` when backward-compatible features are introduced. Fixes and refactors alone normally use a patch increment.

## Automation inventory

The repository has three GitHub Actions workflows:

1. **`CI`** runs for pull requests and pushes on `dev` and `main`, plus manual dispatch. It runs policy validation, Java 17/Spring Boot 3, Java 17/Spring Boot 4, PostgreSQL, quality, and dependency review.
2. **`CodeQL`** runs for pull requests and pushes on `dev` and `main`, on its weekly schedule, and through manual dispatch.
3. **`Release`** runs for pushed signed `v*.*.*` tags and can be manually rerun only for an existing signed version. A merge to `main` alone does not publish. Its protected `maven-central` job uploads the signed bundle; Maven Central publication remains a separate manual approval.

Dependabot is separate from those three GitHub Actions workflows. It creates weekly Maven and GitHub Actions update pull requests targeting `dev`.

## Historical governance boundary

The first 38 `main` commits and signed `v0.1.0` are retained as legacy history. The governance migration adds one final non-release correction commit. The one-release/one-commit invariant starts prospectively at the resulting freeze baseline; no claim is made that historical `main` retroactively contains only release commits.

See [Contributing](../CONTRIBUTING.md) for local checks, [Releasing](../RELEASING.md) for operating steps, and [Architecture](architecture.md) for module boundaries.
