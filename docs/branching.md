# Branch and release workflow

The permanent flow is:

```text
feature/* -> pull request -> dev -> release pull request -> main -> approved tag -> Maven Central
```

## Branch roles

- `feature/*` contains isolated changes and targets `dev` through a pull request.
- `dev` integrates the next snapshot and is the default target for dependency updates.
- `main` is always releasable and receives only reviewed release pull requests from `dev` plus narrowly scoped emergency fixes.
- `vX.Y.Z` tags are immutable and are created only from `main` after release approval.

There is intentionally no `qa` branch. A library has no separately deployed QA runtime; the candidate is the immutable source commit and its artifacts. Automated tests run before merge on `dev`, then again for the exact release candidate on `main`.

## Protection rules

Protect both `dev` and `main` with:

- no direct pushes or force pushes;
- pull requests required;
- all review conversations resolved;
- branches required to be up to date before merging;
- required checks `build-java17-boot3`, `build-java17-boot4`, `postgresql`, and `quality`;
- deletion disabled for permanent branches.

Require at least one approving review when another maintainer is available. Use the normal repository merge strategy consistently and delete merged feature branches.

## Release boundary

Create release tags only from `main`. The publication workflow must use the protected `maven-central` GitHub environment and require manual approval. Validation and upload do not imply permission to publish: the first releases remain pending in Central until a maintainer reviews and publishes them manually.

Snapshots stay on `dev`; released versions never return to a `-SNAPSHOT` version. Published Maven coordinates are immutable.

See [Contributing](../CONTRIBUTING.md) for local checks and [Architecture](architecture.md) for module boundaries.
