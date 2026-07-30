# Releasing CriteriaForge

This guide prepares every CriteriaForge release through the Maven Central Publisher Portal. `main` is the default branch and official release ledger; `dev` is the integration branch. Publishing is intentionally two-stage: the protected `maven-central` job uploads a signed bundle and waits for Central validation, then a maintainer reviews and manually publishes it. Once Maven Central publishes a coordinate, it cannot be replaced, edited, or deleted. Fixes require a new version.

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

Create a GitHub Actions environment named `maven-central`, require a maintainer review, and create these environment secrets exactly:

| Secret | Value |
| --- | --- |
| `CENTRAL_USERNAME` | Central token username |
| `CENTRAL_PASSWORD` | Central token password |
| `GPG_PRIVATE_KEY` | ASCII-armored private key, then base64-encoded as one value |
| `GPG_PASSPHRASE` | Signing-key passphrase |

The Central token should expire and be rotated before its expiration date. Add required reviewers so the deployment job cannot start without manual approval. Do not put secret values in repository-level secrets, environment variables committed to the repository, workflow defaults, Maven POMs, issues, or logs.

## 5. Prepare, verify, and publish a release

Choose `0.1.1` for fixes, `0.2.0` for backward-compatible features, `0.MINOR.0` plus migration notes for breaking `0.x` changes, and `1.0.0` when the public API becomes stable. Fixes and refactors alone normally use a patch increment.

Follow this sequence exactly:

1. Ensure `dev` is green and all intended work is integrated.
2. Create `release/X.Y.Z` from current `dev`.
3. Set every reactor version to the stable `X.Y.Z` version and add the dated `## [X.Y.Z] - YYYY-MM-DD` entry to `CHANGELOG.md`.
4. Run Boot 3, Boot 4, PostgreSQL, quality/documentation, and release-profile verification:

   ```text
   ./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
   ./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
   ./mvnw -B -ntp -Ppostgresql-tests verify
   ./mvnw -B -ntp -Pquality,documentation verify
   ./mvnw -B -ntp -Prelease -Dgpg.skip=true -Dcentral.skipPublishing=true clean verify
   ```

   The release profile creates the main, source, and Javadoc artifacts without signing or contacting Central. Inspect the published module targets; it explicitly excludes `criteriaforge-example` from the Central bundle.
5. Open the release branch pull request to `dev` and squash-merge it. GitHub deletes the merged `release/X.Y.Z` branch automatically.
6. Open the only permitted pull request to `main`: exact source `dev`, exact title `chore(release): release X.Y.Z`, matching stable Maven version `X.Y.Z`, and the dated changelog entry from step 3.
7. Require every check and complete maintainer review. No approving review is required while only one eligible maintainer exists.
8. Squash-merge once, creating the sole release commit on `main`.
9. Wait for post-merge CI and CodeQL to succeed on that exact `main` commit.
10. As a maintainer, create and locally verify the signed `vX.Y.Z` tag on that exact commit, then push the tag:

    ```text
    git tag -s vX.Y.Z -m "Release vX.Y.Z" MAIN_COMMIT
    git tag --verify vX.Y.Z
    git rev-parse vX.Y.Z^{commit}
    git push origin vX.Y.Z
    ```

11. Let the tag push trigger `Release`. It validates the signed candidate and uploads the intended Maven Central artifacts through its protected `maven-central` job. `workflow_dispatch` may rerun an existing signed tag, but it does not replace that tag. A merge to `main` alone never publishes.
12. Approve the protected environment when the workflow requests it, inspect the Central deployment at `VALIDATED`, and publish it manually. Confirm every module, coordinate, POM, source JAR, Javadoc JAR, signature, license, SCM URL, and validation message before selecting **Publish**.
13. Run the failure-safe [`dev` reset procedure](docs/branching.md#controlled-dev-reset-after-a-release). It records and checks the release `main` SHA/tree and open pull requests, disables only exact `protect-dev`, recreates `dev` at the verified `main` commit, restores the ruleset immediately, and verifies protection again.
14. Create a short-lived `chore/next-snapshot` branch from the recreated `dev`, set the next snapshot version, and squash its pull request back into `dev`.

## Recovery

- **Validation failed:** read Central's per-file messages, drop the deployment, and add a regression check. A manual dispatch may rerun only the unchanged existing signed tag. If source or release metadata must change, prepare and merge a new candidate with a new version and signed tag; never reuse the failed tag or version.
- **Workflow failed before upload:** do not move or replace the signed tag. A manual recovery run may repeat verification only for the unchanged existing signed tag. If source or release metadata must change, prepare and merge a new candidate with a new version and signed tag.
- **Signing key exposed:** revoke it, remove GitHub secrets, publish the revocation, create a new key, and rotate the workflow secrets before another release.
- **Central token exposed:** revoke it in Central immediately and replace both token secrets.
- **Wrong artifact already published:** publication cannot be reversed. Publish a corrected patch version and document the affected version.

Follow the exact branch controls and automation inventory in [Branching](docs/branching.md).
