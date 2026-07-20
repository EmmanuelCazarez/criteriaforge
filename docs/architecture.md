# Architecture

CriteriaForge is a modular Java library built around a framework-independent query model. Each integration is kept in its own Maven module so applications can depend only on the capabilities they use.

```text
HTTP query parameters ──> spring-web ──┐
                                      ├──> core QuerySpec ──> JPA query engine ──> database
programmatic callers ──────────────────┘

Spring Boot auto-configuration wires the selected modules when they are present.
```

## Module dependencies

- `criteriaforge-core` depends only on Java. It owns the query AST, policies, results, and stable errors.
- `criteriaforge-jpa` depends on core and Jakarta Persistence. It validates metamodel paths and executes Criteria queries.
- `criteriaforge-spring-web` depends on core. It optionally parses HTTP parameters into `QuerySpec`; it never executes a query.
- `criteriaforge-spring-boot-autoconfigure` provides conditional beans and backs off when the application supplies replacements.
- `criteriaforge-spring-boot-starter` is the convenience dependency for JPA consumers and does not force a web stack.
- `criteriaforge-test-support` helps consumers verify policy contracts.
- `criteriaforge-example` is an unpublished runnable consumer.

Framework-specific modules depend on the smaller modules they extend. Core cannot reference Spring, JPA, servlet APIs, JSON libraries, or RPC libraries; an automated dependency rule enforces this boundary.

## Application responsibilities

CriteriaForge handles query description, validation, and execution. It does not define an application's domain architecture. A consuming application typically:

1. Creates `QuerySpec` from HTTP parameters or application code.
2. Authenticates and authorizes the caller, selects the entity and effective query policy, and applies mandatory scope.
3. Calls `CriteriaForgeExecutor`, which validates and executes the JPA query.
4. Maps `QueryResult` into its own public response contract.

Business decisions, workflows, invariants, authorization, and orchestration remain in the consuming application. CriteriaForge only removes repetitive dynamic-query construction.

## Query execution

The JPA module performs metadata-only preflight validation before creating executable queries. Content and count queries are built independently because Criteria roots and joins cannot be shared between them. Identifier ordering comes from the metamodel. To-many filtering enables distinct content and count behavior. Projection aliases retain complete paths and are assembled into insertion-ordered nested maps.

## Extension points

- Create `QuerySpec` from another input mechanism without changing core or JPA.
- Replace `QueryPolicyResolver` for endpoint- or entity-specific exposure.
- Replace auto-configured beans with normal application beans.
- Build another persistence integration from the core query model only when its operator semantics are explicit and separately tested.

Generic write operations, controllers, response envelopes, and domain abstractions are intentionally outside the project.
