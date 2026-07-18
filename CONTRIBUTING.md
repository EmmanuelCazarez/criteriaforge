# Contributing to CriteriaForge

## Local setup

Requirements are Git and JDK 17 or newer. Use the committed Maven wrapper:

```text
./mvnw -B -ntp clean verify
```

Docker is optional locally and required to execute the PostgreSQL integration test. Without Docker, Testcontainers reports why that test is skipped.

## Development workflow

1. Branch from `dev` using `feature/<short-name>`.
2. Add a failing focused test for behavior changes or bug fixes.
3. Implement the smallest coherent change.
4. Run the focused module, then the complete compatibility checks.
5. Open a pull request into `dev` and describe public API, security, and compatibility effects.

Do not put business rules, transport response formats, generic writes, or organization-specific abstractions into the query engine. Preserve the dependency direction described in [Architecture](docs/architecture.md).

## Required checks

```text
./mvnw -B -ntp clean verify -Dspring-boot.version=3.5.16
./mvnw -B -ntp clean verify -Dspring-boot.version=4.1.0
./mvnw -B -ntp -Ppostgresql-tests verify
./mvnw -B -ntp -Pquality,documentation verify
```

The PostgreSQL command executes against PostgreSQL 17 when Docker is available. CI always has the required container runtime.

## Compatibility and public API

Published modules compile with Java 17. Avoid exposing implementation classes, mutable collections, framework types in core, or behavior that depends on undocumented database coercion. New operators require core validation, type compatibility, H2 coverage, PostgreSQL-sensitive coverage where applicable, and query-language documentation.

The project is in the `0.x` line. Public API changes are still possible, but they must be intentional, documented in [Changelog](CHANGELOG.md), and kept as small as practical.

## Commits and pull requests

Use focused commits with clear prefixes such as `feat`, `fix`, `test`, `docs`, `build`, or `ci`. Keep generated build output and credentials out of commits. A pull request should explain:

- the user-facing problem and behavior;
- the test that proves it;
- policy or data-exposure implications;
- Spring Boot 3.5/4.x and database compatibility implications.

Follow the protected branch flow in [Branching](docs/branching.md). Report security issues privately using [Security reporting](SECURITY.md).
