# Query language

CriteriaForge uses `QuerySpec` as its transport-neutral query model. A specification contains an optional filter expression tree, ordered projection fields, ordered sort fields, and optional offset pagination. All public collections are immutable after construction.

## Programmatic queries

```java
import static io.github.emmanuelcazarez.criteriaforge.core.Filters.*;
import static io.github.emmanuelcazarez.criteriaforge.core.PageSpec.offset;
import static io.github.emmanuelcazarez.criteriaforge.core.SortSpec.desc;

var query = QuerySpec.builder()
    .select("id", "customer.name", "total")
    .where(and(
        eq("status", "PAID"),
        gte("total", "100.00"),
        or(like("customer.name", "Ana%"), in("customer.country", "MX", "US"))))
    .sort(desc("total"))
    .page(offset(0, 20))
    .build();
```

Values enter the model as strings and are converted to the Java type resolved from the JPA metamodel. Supported targets include primitive wrappers, `BigInteger`, `BigDecimal`, booleans, enums, UUIDs, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, and `Instant`.

## Operators

| Core operator | URL suffix | Arity | Valid use |
| --- | --- | ---: | --- |
| `EQ` | `_eq` | 1 | Any supported scalar type |
| `NE` | `_not` | 1 | Any supported scalar type |
| `GT` | `_gt` | 1 | Comparable types |
| `GTE` | `_gte` | 1 | Comparable types |
| `LT` | `_lt` | 1 | Comparable types |
| `LTE` | `_lte` | 1 | Comparable types |
| `LIKE` | `_like` | 1 | Text only; SQL `%` and `_` wildcards are preserved |
| `IN` | `_in` or no suffix | 1+ | Any supported scalar type |
| `BETWEEN` | `_between` | 2 | Comparable types |
| `IS_NULL` | `_isnull=true` | 0 | Any nullable field |
| `IS_NOT_NULL` | `_notnull=true` | 0 | Any nullable field |

`LIKE` is case-insensitive. An incompatible operator, invalid arity, unknown field, or failed conversion produces a stable validation error before query execution.

## HTTP parameter rules

Normal filter parameters are combined with `AND` in encounter order:

```text
status_eq=PAID&total_gte=100
```

`OR_` parameters are alternatives. If normal filters also exist, the expression is `OR(AND(normal filters), each OR_ filter)`:

```text
status_eq=PAID&total_gte=100&OR_status_eq=CREATED
```

means `(status = PAID AND total >= 100) OR status = CREATED`.

The compact URL syntax intentionally supports flat alternatives only. Build `QuerySpec` directly for arbitrarily nested `AND`, `OR`, and `NOT` groups.

Repeated and comma-separated values are equivalent for membership:

```text
status_in=PAID,CREATED
status_in=PAID&status_in=CREATED
status=PAID,CREATED
```

An un-suffixed filter is `IN`, not equality. Unknown operator-looking suffixes are rejected rather than treated as field names.

## Paths and relationships

Paths use dot notation, such as `customer.country`. Root scalar fields are available by default. Relationship traversal must be enabled by policy.

- Filters may traverse to-one and to-many relationships.
- Sorting and projection may traverse root and to-one paths.
- Sorting or projecting through a to-many path is rejected in 0.1.x.
- Reused paths share joins.
- Collection filters use distinct roots and distinct counts to prevent duplicate results.

## Projection

`fields` preserves declared order. A projected nested to-one path becomes a nested map:

```text
fields=id,customer.name,customer.country,total
```

```json
{
  "id": 7,
  "customer": {"name": "Ana", "country": "MX"},
  "total": 125.00
}
```

Call `findAll` when no fields are selected and `findProjected` when fields are present.

## Sorting and pagination

Sort tokens are evaluated left to right. Plain or `+` fields are ascending; `-` fields are descending:

```text
sort=-createdAt,+reference
```

Without explicit sorting, CriteriaForge orders by the entity identifier discovered through the JPA metamodel. It never assumes the identifier is named `id`.

`limit` is required when `offset` is supplied. Offset defaults to zero. `QueryResult` reports `content`, `total`, `offset`, and `limit`; total counts are independent of page size.

See [Security and query policy](security.md) for field exposure and complexity limits.
