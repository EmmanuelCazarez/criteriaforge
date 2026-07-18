# Architecture

CriteriaForge uses ports and adapters to isolate a transport-neutral query model from persistence and framework integration.

```text
HTTP parameters ──> spring-web ──> core QuerySpec
                                      │
application/use case ─────────────────┤
                                      v
                              CriteriaForgeExecutor
                                      │
                                      v
                              JPA Criteria adapter ──> database
```

## Dependency direction

- `criteriaforge-core` depends only on Java. It owns the query AST, policies, results, and stable errors.
- `criteriaforge-jpa` depends on core and Jakarta Persistence. It is an outbound adapter that validates metamodel paths and executes Criteria queries.
- `criteriaforge-spring-web` depends on core. It is an optional inbound adapter that parses HTTP parameters; it never executes a query.
- `criteriaforge-spring-boot-autoconfigure` assembles conditional beans and backs off when the application supplies replacements.
- `criteriaforge-spring-boot-starter` is the convenience dependency for JPA consumers and does not force a web stack.
- `criteriaforge-test-support` helps consumers verify policy contracts.
- `criteriaforge-example` is an unpublished runnable consumer.

Dependencies point inward toward core. Core cannot reference Spring, JPA, servlet APIs, JSON libraries, or RPC libraries; an architecture test enforces this.

## Application ownership

CriteriaForge belongs at the persistence boundary, not in the domain model. A typical flow is:

1. A transport adapter creates `QuerySpec`.
2. An application use case authenticates the caller, selects the allowed entity/view and policy, and adds mandatory scope.
3. `CriteriaForgeExecutor` validates the query and delegates to JPA.
4. The application maps the result to its public response.

Business decisions, workflows, invariants, authorization, and orchestration remain in the application/domain layers. The library only removes repetitive query construction.

## Why it is not API-led connectivity

The library does not prescribe system/process/experience API tiers or service boundaries. Those boundaries often encode organizational ownership and integration topology, while CriteriaForge solves a smaller technical problem inside a service. It works equally in a modular monolith, hexagonal service, or conventional layered Spring application.

## Query execution

The adapter performs metadata-only preflight validation before creating executable queries. Content and count queries are built independently because Criteria roots and joins cannot be shared between them. Identifier ordering comes from the metamodel. To-many filtering enables distinct content and count behavior. Projection aliases retain complete paths and are assembled into insertion-ordered nested maps.

## Extension points

- Create `QuerySpec` from another transport without changing core or JPA.
- Replace `QueryPolicyResolver` for endpoint/entity-specific exposure.
- Replace auto-configured beans with normal application beans.
- Add another persistence adapter around the core AST only if its operator semantics are explicit and separately tested.

Generic write operations, controllers, response envelopes, and domain abstractions are intentionally outside the project.
