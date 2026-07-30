# Releasing CriteriaForge

This guide prepares every CriteriaForge release through the Maven Central
Publisher Portal. Complete account and credential steps once; repeat the
release preparation and publication steps for every version.

Publishing is intentionally two-stage: the Release workflow uploads and waits
for Central validation, then a maintainer reviews and manually publishes.
Once Maven Central publishes a coordinate, it cannot be replaced, edited, or
deleted. Fixes require a new version.

## 1. Create the Central account and namespace

1. Sign in at [Maven Central Portal](https://central.sonatype.com/) with the GitHub account `EmmanuelCazarez`.
2. Open your namespaces and confirm `io.github.emmanuelcazarez` is verified. Central normally provisions the namespace from the GitHub identity automatically.
3. If it is absent, add `io.github.emmanuelcazarez` and follow the repository verification shown by Central before proceeding.
4. Review the current [Central publishing requirements](https://central.sonatype.org/publish/requirements/).

The group ID in every published POM must remain inside that verified namespace.

## 2. Create and protect a signing key

Install GnuPG, then create a passphrase-protected primary key with signing capability:

```text
gpg --full-generate-key
gpg --list-secret-keys --keyid-format long
```

Use your public maintainer identity. Record the full fingerprint, export an encrypted backup, export the private key for CI, and publish the public key to a Central-supported key server:

```text
gpg --armor --export-secret-keys KEY_FINGERPRINT
gpg --armor --export KEY_FINGERPRINT
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_FINGERPRINT
```

Keep the private export, passphrase, and revocation certificate in separate secure backups. Never commit them. Confirm the selected signing key is the primary signing-capable key; Central validation must be able to retrieve its public key.

## 3. Generate a Central user token

In Central Portal, open account settings and generate a user token. The token provides a username and password pair for Maven; it is not the GitHub password. Store both values immediately in a password manager.

## 4. Configure GitHub environment secrets and approval

Create a GitHub Actions environment named `maven-central`, require a maintainer
review, and create these environment secrets exactly:

| Secret | Value |
| --- | --- |
| `CENTRAL_USERNAME` | Central token username |
| `CENTRAL_PASSWORD` | Central token password |
| `GPG_PRIVATE_KEY` | ASCII-armored private key, then base64-encoded as one value |
| `GPG_PASSPHRASE` | Signing-key passphrase |

The Central token should expire and be rotated before its expiration date. Add
required reviewers so the deployment job cannot start without manual approval.
Do not put secret values in repository-level secrets, environment variables
committed to the repository, workflow defaults, Maven POMs, issues, or logs.

## 5. Prepare, verify, and publish a release

1. Choose `0.1.1` for fixes, `0.2.0` for backward-compatible features, `0.MINOR.0` plus migration notes for breaking `0.x` changes, and `1.0.0` when the public API becomes stable.
2. Create `release/X.Y.Z` from current `main`.
3. Change every reactor version from the current snapshot to `X.Y.Z`, set SCM tag to `vX.Y.Z`, and update `CHANGELOG.md`.
4. Run Boot 3, Boot 4, PostgreSQL, quality/documentation, and release-profile verification:

   ```text
   ./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
   ./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
   ./mvnw -B -ntp -Ppostgresql-tests verify
   ./mvnw -B -ntp -Pquality,documentation verify
   ./mvnw -B -ntp -Prelease -Dgpg.skip=true -Dcentral.skipPublishing=true clean verify
   ```

   The release profile creates the main, source, and Javadoc artifacts without
   signing or contacting Central. Inspect the published module targets; it
   explicitly excludes `criteriaforge-example` from the Central bundle.
5. Open `chore(release): prepare X.Y.Z` targeting `main`.
6. Squash-merge after all checks pass and wait for the post-merge `main` CI run.
7. Create annotated signed tag `vX.Y.Z` on the exact successful `main` commit and verify it locally:

   ```text
   git tag -s -a vX.Y.Z -m "Release vX.Y.Z" MAIN_COMMIT
   git tag --verify vX.Y.Z
   git rev-parse vX.Y.Z^{commit}
   ```

8. Push only that tag; the Release workflow verifies the signature and full matrix before requesting `maven-central` approval. It validates the immutable tagged candidate with trusted release tooling from protected `main`, so a manual recovery run may only name the same existing signed tag.
9. Approve the environment, inspect the Central deployment at `VALIDATED`, and publish it manually. Confirm every module, coordinate, POM, source JAR, Javadoc JAR, signature, license, SCM URL, and validation message before selecting **Publish**.
10. Verify the public coordinates, then publish the GitHub Release from the unchanged signed tag.
11. Open and squash a temporary `chore/next-snapshot` pull request that advances to the next planned snapshot.

## Recovery

- **Validation failed:** read Central's per-file messages, drop the deployment, add a regression check, and upload a corrected candidate.
- **Workflow failed before upload:** do not move or replace the signed tag. A manual recovery run may repeat verification only for that existing tag. If source or release metadata must change, prepare and merge a new release candidate and use a new version and signed tag.
- **Signing key exposed:** revoke it, remove GitHub secrets, publish the revocation, create a new key, and rotate the workflow secrets before another release.
- **Central token exposed:** revoke it in Central immediately and replace both token secrets.
- **Wrong artifact already published:** publication cannot be reversed. Publish a corrected patch version and document the affected version.

Follow the exact branch controls in [Branching](docs/branching.md).
