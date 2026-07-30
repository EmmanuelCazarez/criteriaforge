# Contributing to CriteriaForge

## Local setup

Requirements are Git and JDK 17 or newer. Use the committed Maven wrapper:

```text
./mvnw -B -ntp clean verify
```

Docker is optional locally and required to execute the PostgreSQL integration test. Without Docker, Testcontainers reports why that test is skipped.

## Branch and pull-request workflow

`main` is the default branch and the official release ledger. `dev` is the integration branch. Contributors create work from the current `dev` tip and open their pull requests to `dev`; no contributor branch targets `main`.

Same-repository temporary branches must use one of these approved prefixes:

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

External forks may use their own branch names, but their pull requests also target `dev`. Every pull request to `dev` needs a Conventional Commit title. Use focused commits with clear types such as `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`, or `revert`; scopes are optional.

All permitted pull requests use squash merge. After a same-repository short-lived branch is merged, GitHub deletes it automatically. GitHub cannot delete a branch in a contributor fork, so fork owners delete those branches themselves when they are no longer needed. `dev` is the only source branch permitted to use `main` as its destination, and its release pull request has the exact title `chore(release): release X.Y.Z`.

1. Branch from current `dev` using an approved same-repository prefix, or use any branch name in a fork.
2. Add a failing focused test for behavior changes or fixes.
3. Implement the smallest coherent change.
4. Run focused tests and the complete compatibility checks.
5. Open a pull request targeting `dev` with a Conventional Commit title and complete compatibility and security notes.
6. Resolve conversations and update the branch with current `dev`.
7. Squash-merge after every required check passes; allow automatic deletion of same-repository source branches.

Do not put business rules, transport response formats, generic writes, or organization-specific abstractions into the query engine. Preserve the dependency direction described in [Architecture](docs/architecture.md).

## Required checks

```text
./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
./mvnw -B -ntp -Ppostgresql-tests verify
./mvnw -B -ntp -Pquality,documentation verify
```

The PostgreSQL command executes against PostgreSQL 17 when Docker is available. CI always has the required container runtime. Pull requests to either permanent branch require the remote `pr-policy`, `dependency-review`, and `codeql-java` checks, in addition to the Boot 3, Boot 4, PostgreSQL, and quality checks. `pr-policy` verifies the Conventional Commit title and the approved prefix for a same-repository branch targeting `dev`; for `main`, it allows only exact `dev` with the exact release title.

## Compatibility and public API

Published modules compile with Java 17. Avoid exposing implementation classes, mutable collections, framework types in core, or behavior that depends on undocumented database coercion. New operators require core validation, type compatibility, H2 coverage, PostgreSQL-sensitive coverage where applicable, and query-language documentation.

The project is in the `0.x` line. Public API changes are still possible, but they must be intentional, documented in [Changelog](CHANGELOG.md), and kept as small as practical. Use `0.2.0` when a release introduces backward-compatible features. Fixes and refactors alone normally use a patch increment.

## Commits and pull requests

Keep generated build output and credentials out of commits. A pull request should explain:

- the user-facing problem and behavior;
- the test that proves it;
- policy or data-exposure implications;
- Spring Boot 3.5/4.x and database compatibility implications.

Follow the protected branch flow in [Branching](docs/branching.md) and the release operation in [Releasing](RELEASING.md). Report security issues privately using [Security reporting](SECURITY.md).
