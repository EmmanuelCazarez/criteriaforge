# Security and query policy

A dynamic-query endpoint makes field names, operators, joins, and result sizes
part of a public surface. CriteriaForge validates that surface, but it does not
authorize callers or decide which rows they may access. Authentication, tenant
or ownership restrictions, soft-delete rules, and use-case decisions remain
application responsibilities.

## Register every exposed entity

Spring Boot auto-configuration is fail-closed. It creates the query engine, but
an entity cannot be queried until the application registers exactly one policy:

```java
@Bean
QueryPolicyRegistration orderQueryPolicy() {
    var policy = QueryPolicy.builder()
        .allowFields("id", "reference", "status")
        .alias("amount", "total")
        .alias("customerName", "customer.name")
        .allowOperators("amount", Operator.EQ, Operator.GTE, Operator.LTE)
        .relationshipTraversal(true)
        .maxDepth(1)
        .maxPageSize(50)
        .maxConditions(12)
        .build();

    return QueryPolicyRegistration.forEntity(Order.class, policy);
}
```

No registration produces `QUERY_POLICY_NOT_FOUND`. Two registrations for the
same entity fail application startup. There is no global fallback policy that
accidentally exposes a newly queried entity.

Public aliases are resolved before JPA metadata access, but policy errors refer
to the public name. In the example, callers use `amount` instead of `total`.
Aliases are valid for filtering, sorting, and projection; per-request projection
aliases only change output shape.

## Policy defaults

Each `QueryPolicy` begins with these limits:

- relationship traversal disabled;
- maximum page size 100;
- maximum 25 conditions;
- maximum relationship depth 2.

When no explicit allowlist is configured, persistent fields are eligible unless
hidden. For a public endpoint, prefer an allowlist. Calling `alias(...)` adds the
public name to an explicit allowlist; it does not make an alias-only policy
silently restrict all other fields.

Explicit denials override allowlists. An operator allowlist applies to its public
field; fields without one retain the operators compatible with their resolved
Java type.

## Hide sensitive fields

`@QueryHidden` prevents filtering, sorting, or projecting a persistent field,
including through a public alias:

```java
import io.github.emmanuelcazarez.criteriaforge.core.annotation.QueryHidden;

@Entity
class Customer {
    @QueryHidden
    private String internalRiskScore;
}
```

Do not rely on names such as `secret` or `password`. Mark sensitive attributes
and add policy tests.

## Apply mandatory row restrictions in the application

CriteriaForge intentionally has no generic query-scope abstraction. The
application knows whether a tenant, ownership, region, soft-delete, or other
mandatory condition is correct for a use case. Add those constraints before
execution, expose a use-case-specific service, or enforce them with a database
or persistence mechanism appropriate to the application.

Never pass a caller-controlled entity class or policy registration into
`QueryEngine.execute(...)`.

## Test the boundary

At minimum, verify:

```java
assertThat(policy.isFieldAllowed("reference")).isTrue();
assertThat(policy.isFieldAllowed("internalRiskScore")).isFalse();
assertThat(policy.resolveField("amount")).isEqualTo("total");
assertThat(policy.isOperatorAllowed("amount", Operator.GTE)).isTrue();
assertThat(policy.isOperatorAllowed("amount", Operator.LIKE)).isFalse();
```

Also integration-test unknown fields, hidden fields, relationship depth,
oversized pages, excessive conditions, invalid conversions, and missing entity
registrations. These failures occur before SQL execution.

## Operational guidance

- Keep relationship traversal off unless an endpoint needs it.
- Use smaller query and parser limits for internet-facing endpoints.
- Monitor validation failures and slow queries without logging raw secrets.
- Map stable error codes to the application's API contract.
- Do not expose stack traces, generated SQL, or database messages.
- Review entity and policy changes as public API changes.
- Use database statement timeouts and resource limits as defense in depth.

For vulnerability disclosure, follow [Security reporting](../SECURITY.md).
