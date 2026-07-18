# Security and query policy

A dynamic-query endpoint makes field names, operators, joins, and result sizes part of your public API. CriteriaForge validates that surface, but it does not decide whether the caller is authorized to read an entity or row. Apply authentication, tenant scoping, ownership checks, and use-case rules before executing a query.

## Safe defaults

The default `QueryPolicy`:

- permits persistent root scalar fields unless hidden;
- disables relationship traversal;
- caps a page at 100 rows;
- caps a query at 25 conditions;
- caps relationship depth at 2.

Unknown paths, non-persistent properties, incompatible operators, conversion failures, and excessive complexity fail before SQL is executed.

## Hide sensitive fields

`@QueryHidden` prevents filtering, sorting, or projecting a field:

```java
import io.github.emmanuelcazarez.criteriaforge.core.annotation.QueryHidden;

@Entity
class Customer {
    @QueryHidden
    private String internalRiskScore;
}
```

Do not rely on naming conventions such as `secret` or `password`. Mark every sensitive persistent field explicitly and add a policy test.

## Prefer allowlists for public APIs

Supply a `QueryPolicyResolver` bean to replace the global default and resolve policy by entity:

```java
@Bean
QueryPolicyResolver queryPolicyResolver() {
    return entityType -> {
        if (entityType == Order.class) {
            return QueryPolicy.builder()
                .allowFields("id", "reference", "status", "total", "customer.name")
                .allowOperators("total", Operator.EQ, Operator.GTE, Operator.LTE)
                .relationshipTraversal(true)
                .maxDepth(1)
                .maxPageSize(50)
                .maxConditions(12)
                .build();
        }
        return QueryPolicy.builder().allowFields("id").build();
    };
}
```

Explicit denials override allowlists. An operator allowlist applies only to its configured field; fields without one retain type-compatible operators.

Global defaults can be configured when a custom resolver is unnecessary:

```yaml
criteriaforge:
  query:
    max-page-size: 50
    max-conditions: 12
    max-depth: 1
    relationship-traversal: false
```

## Test the policy

Add `criteriaforge-test-support` with test scope and assert the public surface:

```java
import static io.github.emmanuelcazarez.criteriaforge.test.QueryPolicyAssertions.assertThat;

assertThat(policy)
    .hasMaximumPageSize(50)
    .doesNotAllowRelationshipTraversal()
    .allowsFields("id", "reference", "status")
    .deniesFields("internalRiskScore");
```

## Operational guidance

- Apply row-level access predicates in your application; a field allowlist is not row authorization.
- Keep relationship traversal off unless an endpoint needs it.
- Prefer smaller limits for internet-facing endpoints.
- Monitor validation failures and slow queries without logging raw secrets.
- Map stable error codes to your API contract; do not expose stack traces, generated SQL, or database messages.
- Review entity changes as API changes when root fields are discoverable.
- Use database statement timeouts and resource limits as defense in depth.

For vulnerability disclosure, follow [Security reporting](../SECURITY.md).
