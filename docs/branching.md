# Branch and release workflow

The permanent flow is:

```text
temporary branch or fork -> pull request -> required checks -> squash merge -> main
```

`main` is the only permanent and default branch. There is no `dev` or `qa`
branch in the final model.

## Temporary branches and pull requests

Same-repository temporary branches must use one of these prefixes:

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

External fork branch names are accepted. The prefix policy applies only to
branches owned by the CriteriaForge repository.

Temporary branches may contain multiple development or fixup commits. GitHub
writes one squash commit to `main` for each merged pull request. Pull request
titles become that commit title and must use an approved Conventional Commit
type: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`,
`perf`, or `revert`. Scopes are optional.

Only squash merging is enabled. Resolve review conversations and update the
pull request with current `main` before merging. Same-repository source
branches are automatically deleted after merge; GitHub cannot delete a branch
in a contributor's fork.

## Required checks and protection

Pull requests to `main` require these exact checks:

- `pr-policy`
- `build-java17-boot3`
- `build-java17-boot4`
- `postgresql`
- `quality`
- `dependency-review`
- `codeql-java`

The `main` ruleset also blocks direct pushes, force pushes, and branch
deletion, requires pull requests to be current with `main`, and requires all
review conversations to be resolved. The repository requires an approving
review when another maintainer is available.

## Release boundary

A merge to `main` runs CI but does not publish. Release preparation is a
temporary `release/X.Y.Z` pull request; after its successful post-merge CI
run, an approved annotated signed `vX.Y.Z` tag on that exact `main` commit
starts release verification. The Maven version is `X.Y.Z` without the `v`.

The Release workflow verifies the signed, immutable tagged candidate and the
full compatibility matrix before it requests approval for the protected
`maven-central` environment. It does not publish automatically: a maintainer
inspects the validated Central deployment and publishes it manually. A GitHub
Release is created only after the public Maven coordinates are available.

Published Maven coordinates and release tags are immutable. After publication,
a temporary `chore/next-snapshot` pull request advances `main` to the next
planned snapshot.

See [Contributing](../CONTRIBUTING.md) for local checks, [Releasing](../RELEASING.md)
for release operations, and [Architecture](architecture.md) for module
boundaries.
