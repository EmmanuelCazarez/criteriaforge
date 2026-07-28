# Query language

CriteriaForge uses `QueryRequest` as its transport-neutral model. A request can
contain one filter expression, ordered projections, ordered sorts, and offset
pagination. The same model can be created by application code or by the optional
Spring Web adapter.

## Programmatic queries

Programmatic filters keep their Java values typed:

```java
import static io.github.emmanuelcazarez.criteriaforge.core.Filters.field;

var filter = field("status").eq(OrderStatus.PAID)
    .and(field("amount").gte(minimumTotal))
    .and(
        field("customer.country").eq("MX")
            .or(field("customer.country").eq("US"))
    )
    .and(field("cancelledAt").isNull());

var query = QueryRequest.builder()
    .select("reference")
    .selectAs("customerName", "buyer.name")
    .selectAs("amount", "orderTotal")
    .where(filter)
    .orderByDescending("amount")
    .offset(0)
    .limit(20)
    .build();
```

Add `orderByAscending` and `orderByDescending` calls in priority order.
`limit` defaults the offset to zero; an explicit `offset` must be accompanied
by `limit`.

Use `Filters.allOf(collection)`, `Filters.anyOf(collection)`, and
`Filters.not(expression)` when expressions are collected dynamically.
`FilterExpression` is intentionally opaque; consumers do not construct its
internal nodes.

Enums, `BigDecimal`, dates, UUIDs, booleans, and other values supplied by Java
remain typed. String values from HTTP are converted to the Java type discovered
through the JPA metamodel. A non-string Java value that does not match the
resolved field type is rejected rather than silently coerced.

The runnable example builds a complete request in
`OrderSearchService`, demonstrating that Spring Web is optional.

## Readable HTTP filters

Use one `filter` parameter for nested boolean expressions:

```text
filter=status == PAID and (amount >= 100 or customer.country in ("MX","US"))
```

The grammar supports:

| Operation | Syntax |
|---|---|
| Equal / not equal | `status == PAID`, `status != CANCELLED` |
| Ordering | `total > 10`, `total >= 10`, `total < 20`, `total <= 20` |
| Text pattern | `reference like "ORD-%"` |
| Membership | `country in ("MX","US")` |
| Range | `total between 10 and 100` |
| Null checks | `cancelledAt is null`, `cancelledAt is not null` |
| Boolean composition | `and`, `or`, `not`, parentheses |

Keywords are case-insensitive. Values can be unquoted when they contain no
spaces or delimiters. Single- and double-quoted values are supported; a
backslash escapes the following character.

Precedence, from strongest to weakest, is:

1. Parentheses
2. `not`
3. Comparison operators
4. `and`
5. `or`

The default maximum filter length is 4096 characters and the default nested
expression depth is 20. Spring Boot consumers can lower these limits:

```yaml
criteriaforge:
  web:
    max-filter-length: 2048
    max-expression-depth: 12
```

## Compact HTTP filters

The original flat syntax remains useful for simple requests:

```text
status_eq=PAID&total_gte=100&reference_like=ORD-%
```

| Core operator | Compact suffix | Arity |
|---|---|---:|
| `EQ` | `_eq` | 1 |
| `NE` | `_not` | 1 |
| `GT` | `_gt` | 1 |
| `GTE` | `_gte` | 1 |
| `LT` | `_lt` | 1 |
| `LTE` | `_lte` | 1 |
| `LIKE` | `_like` | 1 |
| `IN` | `_in` or no suffix | 1+ |
| `BETWEEN` | `_between` | 2 |
| `IS_NULL` | `_isnull=true` | 0 |
| `IS_NOT_NULL` | `_notnull=true` | 0 |

Repeated and comma-separated values are equivalent for `IN`. Normal compact
filters are combined with `AND`. `OR_` parameters are flat alternatives:

```text
status_eq=PAID&total_gte=100&OR_status_eq=CREATED
```

means `(status == PAID and total >= 100) or status == CREATED`.

Do not mix a readable `filter` expression with compact filter parameters in one
request. CriteriaForge rejects the request instead of guessing precedence.

## Public field names

Entity policies can expose stable query names independently of JPA attributes:

```java
var policy = QueryPolicy.builder()
    .allowFields("id", "reference", "status")
    .alias("amount", "total")
    .alias("customerName", "customer.name")
    .allowOperators("amount", Operator.EQ, Operator.GTE, Operator.LTE)
    .relationshipTraversal(true)
    .build();
```

Callers then filter, sort, and select with `amount` and `customerName`. The
mapping is stable for the entity policy; changing a response key remains a
per-request concern.

## Projection and output aliases

Without `fields`, `QueryEngine.execute(...)` returns full entities. With
`fields`, it returns insertion-ordered nested maps.

```text
fields=reference,customerName as buyer.name,amount as orderTotal
```

```json
{
  "reference": "ORD-102",
  "buyer": {"name": "Luis"},
  "orderTotal": 250.00
}
```

The Java equivalent is:

```java
QueryRequest.builder()
    .select("reference")
    .selectAs("customerName", "buyer.name")
    .selectAs("amount", "orderTotal")
    .build();
```

Source and output paths are separate. Request order is preserved, selected null
values are present explicitly, and nested output paths create nested maps.
Duplicate sources, duplicate outputs, and parent/child output collisions are
rejected. Projection through a to-many relationship is not supported in 0.1.x.

## Paths, sorting, and pagination

Paths use dot notation. Filters may traverse to-one and to-many relationships
when the entity policy allows it. Sorting and projection may traverse root and
to-one paths. Reused paths share joins; to-many filters use distinct roots and
distinct counts.

Sort tokens are evaluated left to right. Plain or `+` fields are ascending and
`-` fields are descending:

```text
sort=-amount,+reference
```

Without an explicit sort, CriteriaForge orders by the JPA identifier discovered
from the metamodel. `limit` is required when `offset` is present; offset defaults
to zero. `QueryResult` contains `content`, `total`, `offset`, and `limit`.

See [Security and query policy](security.md) before exposing a query endpoint.
