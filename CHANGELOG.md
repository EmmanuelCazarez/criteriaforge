# Changelog

All notable changes to CriteriaForge are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Immutable transport-neutral query AST with nested `AND`, `OR`, and `NOT` expressions.
- Typed JPA Criteria filters, to-one projections, sorting, offset pagination, and correct distinct counts.
- Stable validation errors, field/operator policies, `@QueryHidden`, and bounded query complexity.
- Optional Spring Web parameter parser and Spring Boot auto-configuration/starter.
- Consumer policy assertions and runnable H2 example.
- PostgreSQL 17 integration coverage and Spring Boot 3.5/4.x compatibility gates.
- Fluent typed filter expressions for application-built queries.
- Readable HTTP filter expressions with boolean precedence and bounded parsing.
- Stable per-entity public field mappings and per-request projection output aliases.
- Explicit fail-closed per-entity policy registrations.

## [0.1.0] - Unreleased

First public release candidate.

[Unreleased]: https://github.com/EmmanuelCazarez/criteriaforge/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/EmmanuelCazarez/criteriaforge/releases/tag/v0.1.0
