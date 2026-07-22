# CriteriaForge

CriteriaForge turns an immutable query request into validated Jakarta Persistence Criteria queries. It gives Spring applications rich filters, projections, sorting, and offset pagination without adding repository methods for every combination—and without putting REST or business logic in the query engine.

CriteriaForge is a query library, not an API architecture or generic CRUD framework. Your application still owns authorization, use cases, domain rules, controllers, and response contracts.

## Five-minute installation

CriteriaForge requires Java 17 or newer and a Spring Data JPA application.

Maven:

```xml
<dependency>
  <groupId>io.github.emmanuelcazarez</groupId>
  <artifactId>criteriaforge-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>io.github.emmanuelcazarez</groupId>
  <artifactId>criteriaforge-spring-web</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation "io.github.emmanuelcazarez:criteriaforge-spring-boot-starter:0.1.0"
implementation "io.github.emmanuelcazarez:criteriaforge-spring-web:0.1.0"
```

The web module is deliberately separate. Omit it when another transport creates `QueryRequest` directly.

## One controller, many queries

```java
@RestController
@RequestMapping("/api/orders")
class OrderQueryController {
    private final QueryEngine queryEngine;

    OrderQueryController(QueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    @GetMapping
    QueryResult<?> findAll(@DynamicQuery QueryRequest query) {
        return queryEngine.execute(Order.class, query);
    }
}
```

This request filters, projects a nested to-one field, sorts, and paginates:

```text
GET /api/orders?status_eq=PAID&total_gte=100&fields=id,customer.name,total&sort=-total&limit=20
```

The controller contains no Criteria API or field-specific query logic. See the runnable [`criteriaforge-example`](criteriaforge-example/) for entity policies, dummy H2 data, and error mapping.

## Supported URL syntax

| Capability | Syntax | Example |
| --- | --- | --- |
| Equal / not equal | `_eq`, `_not` | `status_eq=PAID` |
| Ordering | `_gt`, `_gte`, `_lt`, `_lte` | `total_gte=100` |
| Text pattern | `_like` | `reference_like=ORD-%` |
| Membership | `_in` or no suffix | `status_in=PAID,CREATED` |
| Range | `_between` | `total_between=10,100` |
| Null checks | `_isnull`, `_notnull` with `true` | `closedAt_isnull=true` |
| Alternatives | `OR_` prefix | `OR_status_eq=CREATED` |
| Projection | `fields` | `fields=id,customer.name` |
| Sorting | `sort`, prefix `-` for descending | `sort=-createdAt,reference` |
| Pagination | `limit` and optional `offset` | `limit=20&offset=40` |

Repeated values and comma-separated values are both accepted. Full semantics and the programmatic nested boolean API are in [Query language](docs/query-language.md).

## Security first

Dynamic filtering exposes part of your persistence model as an API. Do not treat it as authorization. Root scalar fields are queryable by default, relationship traversal is disabled by default, and page/condition/depth limits are enforced. Mark secrets with `@QueryHidden` and define explicit `QueryPolicy` allowlists for public endpoints. Read [Security and query policy](docs/security.md) before exposing a query endpoint.

## Compatibility

| CriteriaForge | Java | Jakarta Persistence | Spring Boot |
| --- | --- | --- | --- |
| 0.1.x | 17+ | 3.x | 3.5.x and 4.x |

CI verifies Java 17 against Spring Boot 3.5.16 and 4.1.0, plus H2 and PostgreSQL behavior.

## Modules

| Module | Purpose |
| --- | --- |
| `criteriaforge-core` | Query requests, filters, policies, results, and errors; pure Java |
| `criteriaforge-jpa` | Typed JPA Criteria execution, joins, projections, counts, and pagination |
| `criteriaforge-spring-web` | Optional URL parser and `@DynamicQuery` argument resolver |
| `criteriaforge-spring-boot-autoconfigure` | Conditional beans and configuration properties |
| `criteriaforge-spring-boot-starter` | Normal JPA consumer dependency; does not force a web stack |
| `criteriaforge-example` | Unpublished runnable H2 application |

## Documentation

- [Query language](docs/query-language.md)
- [Security and query policy](docs/security.md)
- [Architecture](docs/architecture.md)
- [Branch and release flow](docs/branching.md)
- [Contributing](CONTRIBUTING.md)
- [Release guide](RELEASING.md)
- [Security reporting](SECURITY.md)
- [Changelog](CHANGELOG.md)

Licensed under the [Apache License 2.0](LICENSE).
