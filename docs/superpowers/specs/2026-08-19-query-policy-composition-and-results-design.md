# CriteriaForge 0.2 Query Policy, Composition, and Results Design

## Goal

Expand CriteriaForge's reusable query APIs for application-service use without
moving authorization decisions or transport envelopes into the library. The
release adds typed result mapping, immutable mandatory-filter composition,
operation-specific field permissions, bounded projection and sorting
complexity, and deterministic ordering for every offset-paginated query.

This is a public API expansion targeting CriteriaForge 0.2.0. Cursor pagination
and Operations API response envelopes are explicitly outside this design.

## Compatibility Baseline

- Java 17 remains the minimum runtime and compilation baseline.
- Spring Boot 3.5 remains the dependency-management baseline.
- Spring Boot 4.1 remains a required compatibility build.
- Jakarta Persistence 3 remains the persistence API baseline.
- Existing `QueryEngine.execute(Class<?>, QueryRequest)` remains available.
- Existing unified `QueryPolicy.allowFields(...)` APIs remain available but are
  deprecated in favor of operation-specific permissions.

## Query Results

`QueryResult<T>` gains:

```java
public <R> QueryResult<R> map(Function<? super T, ? extends R> mapper)
public boolean hasNext()
public boolean hasPrevious()
```

`map` applies the mapper to every content element and returns a new immutable
result with the original `total`, `offset`, and `limit`. A null mapper is
rejected. Null mapped elements remain invalid because the `QueryResult`
constructor defensively copies its content with `List.copyOf`.

`hasNext()` returns `((long) offset + content.size()) < total`. The `long` cast
prevents integer overflow. `hasPrevious()` returns `offset > 0 && total > 0`.
Neither method introduces page-number semantics.

To make mapping useful without casts, `QueryEngine` gains:

```java
default <T> QueryResult<T> executeEntities(
    Class<T> entityType,
    QueryRequest query)
```

The default method rejects requests containing projection fields, delegates to
the existing `execute` method, and maps each item through `entityType::cast`.
This keeps existing third-party `QueryEngine` implementations source-compatible.
`JpaQueryEngine` overrides the method and delegates directly to its typed entity
execution path.

Dynamic projection requests continue to use `execute(...)` and return
`QueryResult<?>`; projection result typing is not expanded in this release.

## Immutable Query Composition

`QueryRequest` gains:

```java
public QueryRequest andWhere(FilterExpression requiredFilter)
```

The method rejects null, combines the required expression with the request's
existing expression using `AND`, and puts the required expression first. If the
request has no filter, the required expression becomes the complete filter. It
returns a new `QueryRequest` while preserving projection fields, sorting,
pagination, and the original request.

The method is intentionally named for query composition rather than
authorization. Applications decide which tenant, ownership, or visibility
predicate is mandatory; CriteriaForge only preserves it structurally.

## Operation-Specific Field Permissions

`QueryPolicy.Builder` gains:

```java
allowProjectionFields(String... fields)
allowProjectionFields(Collection<String> fields)
allowFilterFields(String... fields)
allowFilterFields(Collection<String> fields)
allowSortFields(String... fields)
allowSortFields(Collection<String> fields)
```

`QueryPolicy` gains immutable accessors and checks:

```java
Set<String> projectionFields()
Set<String> filterFields()
Set<String> sortFields()
boolean isProjectionAllowed(String field)
boolean isFilterAllowed(String field)
boolean isSortAllowed(String field)
```

Each allowlist has an explicit configured flag. An unconfigured operation is
unrestricted, preserving current default behavior. Calling an operation's
allowlist method, including with an empty collection, enables that allowlist;
only its listed public names are then permitted.

`denyFields(...)` remains global and always wins. `@QueryHidden`, relationship
traversal, relationship depth, operator compatibility, and per-field operator
rules continue to apply independently.

Aliases only translate a public field name to a persistent path. In the new
operation-specific mode, declaring an alias grants no projection, filtering,
or sorting permission by itself.

### Legacy Unified Permissions

The following APIs remain in 0.2.0 and are deprecated:

```java
Builder allowFields(String... fields)
Builder allowFields(Collection<String> fields)
Set<String> allowedFields()
boolean isFieldAllowed(String field)
```

Legacy unified configuration applies the same allowlist to projection,
filtering, and sorting and retains the 0.1 alias behavior. A builder may use
either unified permissions or operation-specific permissions, never both.
Mixing the modes throws `IllegalStateException` during builder configuration.

For policies built with operation-specific permissions, deprecated
`allowedFields()` returns the finite intersection of the configured
operation allowlists, treating an unconfigured operation as unrestricted.
Deprecated `isFieldAllowed(field)` returns true only when projection,
filtering, and sorting all allow the field. With no configured allowlist,
`allowedFields()` remains empty and `isFieldAllowed(field)` remains true unless
the field is globally denied, preserving the existing empty-means-unrestricted
convention.

This preserves 0.1 source behavior while ensuring new policies cannot
accidentally make a selectable field filterable or sortable.

## Projection and Sort Complexity

`QueryPolicy` gains:

```java
int maxProjectionFields()
int maxSortFields()
```

with builder methods of the same names. Defaults are:

```java
DEFAULT_MAX_PROJECTION_FIELDS = 20
DEFAULT_MAX_SORT_FIELDS = 5
```

Both limits must be at least one. `QueryComplexityValidator` checks the number
of visible requested projection fields and requested sort orders before JPA
query construction. Violations use new stable error codes:

```java
PROJECTION_LIMIT_EXCEEDED
SORT_LIMIT_EXCEEDED
```

The policy tie-breaker is not caller-controlled and does not count toward
`maxSortFields`.

## Deterministic Tie-Breaking

Every JPA content query has a deterministic final order.

By default, the entity's single JPA primary key is appended in ascending order
unless the effective requested sort already contains that persistent field.
This applies both when callers provide no sort and when they provide one or
more sort orders.

`QueryPolicy.Builder` provides an optional override:

```java
tieBreaker(String field)
tieBreaker(String field, SortDirection direction)
```

The default overload uses ascending direction. The configured field is a
public policy field and is resolved through aliases. It is a policy-author
contract that the configured field uniquely orders matching rows; CriteriaForge
cannot infer every database uniqueness constraint from portable JPA metadata.
`QueryPolicy` exposes it as `Optional<Sorting.Order> tieBreaker()`.

The tie-breaker is trusted policy configuration rather than caller input. It
does not require sort allowlist permission and may use a field denied to
dynamic callers. It must still resolve to a known scalar or to-one path and
must obey relationship traversal and depth constraints. To-many paths are
rejected before SQL executes.

If a caller already sorts by the same resolved persistent path, CriteriaForge
does not append a duplicate order. The caller's requested direction wins.

For distinct projection queries over plural joins, the effective tie-breaker
is added as a hidden selection when required by the database. It never appears
in the returned projection map.

## Validation Flow

Execution remains:

1. Resolve the registered entity policy.
2. Validate page, condition, projection, and sort complexity.
3. Validate filtering fields with `isFilterAllowed`.
4. Validate projection fields with `isProjectionAllowed`.
5. Validate requested sorting fields with `isSortAllowed`.
6. Resolve and validate the effective tie-breaker as trusted policy ordering.
7. Build and execute the JPA content and count queries.

All policy and complexity failures occur before SQL execution.

## Testing

Core tests cover result mapping and navigation, immutable query composition,
permission-mode compatibility, independent permissions, alias behavior,
complexity defaults, limits, and stable error codes.

JPA integration tests cover independent operation rejection before SQL,
primary-key tie-breaking after requested sorts, configured tie-breakers,
duplicate suppression, hidden distinct-projection ordering, and invalid
tie-breaker paths.

The complete reactor must pass against Spring Boot 3.5 and Spring Boot 4.1.
PostgreSQL and quality profiles remain required release checks.

## Excluded Work

- Cursor or keyset pagination.
- Page-number metadata or page-count helpers.
- Operations API envelopes.
- Authorization policy decisions or current-user resolution.
- Publishing, tagging, or Maven Central release operations.
