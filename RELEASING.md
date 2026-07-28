# Releasing CriteriaForge

This guide prepares the first `0.1.0` publication through the Maven Central Publisher Portal. Complete account and credential steps once; repeat the candidate and publication steps for every version.

Publishing is intentionally two-stage: GitHub Actions uploads and waits for Central validation, then a maintainer reviews and manually publishes. Once Maven Central publishes a coordinate, it cannot be replaced, edited, or deleted. Fixes require a new version.

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

## 5. Verify a candidate locally

The development version remains `0.1.0-SNAPSHOT`. Before the release pull request, run:

```text
./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
./mvnw -B -ntp -Pquality,documentation verify
./mvnw -B -ntp -Prelease -Dgpg.skip=true -Dcentral.skipPublishing=true clean verify
```

The last command creates the main, source, and Javadoc artifacts without signing or contacting Central. Inspect the published module targets. The release profile explicitly excludes `criteriaforge-example` from the Central bundle.

## 6. Prepare version 0.1.0

1. Ensure [Changelog](CHANGELOG.md) describes the candidate and replaces `Unreleased` for `0.1.0` with the real date.
2. Change all reactor versions from `0.1.0-SNAPSHOT` to `0.1.0` using Maven's versions tooling or one reviewed mechanical change.
3. Run the complete verification matrix again.
4. Commit the version change on `dev` and open the release pull request from `dev` to `main`.
5. Wait for all required checks and review conversations, then merge.
6. Create signed tag `v0.1.0` from that exact `main` commit and push only after checking the commit and version.
7. Publish a GitHub Release for `v0.1.0`, or manually dispatch the Release workflow with version `0.1.0` from that tag.

The workflow rejects snapshot versions, mismatched tags, and commits that are not contained in `main`.

## 7. Approve upload, inspect, then publish

1. Review the pending `maven-central` environment deployment in GitHub and approve it.
2. The workflow tests, signs, uploads, and waits until Central reports `VALIDATED`. It uses `autoPublish=false`.
3. Open Central Portal deployments. Inspect every module, coordinate, POM, source JAR, Javadoc JAR, signature, license, SCM URL, and validation message.
4. If everything is exact, choose **Publish** in Central Portal.
5. If anything is wrong, choose **Drop**. Correct the source and use a new candidate. Never publish a questionable bundle to “fix later.”
6. After publication, wait for synchronization and verify a clean sample project can resolve `io.github.emmanuelcazarez:criteriaforge-spring-boot-starter:0.1.0` from Maven Central.

Published coordinates are immutable. If `0.1.0` contains a defect, publish `0.1.1`; do not attempt to overwrite `0.1.0`.

## Recovery

- **Validation failed:** read Central's per-file messages, drop the deployment, add a regression check, and upload a corrected candidate.
- **Workflow failed before upload:** fix the workflow/build on `dev`, repeat the release pull request, and create a new approved tag only if the release commit changes.
- **Signing key exposed:** revoke it, remove GitHub secrets, publish the revocation, create a new key, and rotate the workflow secrets before another release.
- **Central token exposed:** revoke it in Central immediately and replace both token secrets.
- **Wrong artifact already published:** publication cannot be reversed. Publish a corrected patch version and document the affected version.

Follow the exact branch controls in [Branching](docs/branching.md).
