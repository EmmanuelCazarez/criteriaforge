package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/** Binds one JPA entity type to its explicit dynamic-query policy. */
public record QueryPolicyRegistration(Class<?> entityType, QueryPolicy policy) {

    public QueryPolicyRegistration {
        entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public static QueryPolicyRegistration forEntity(
            Class<?> entityType, QueryPolicy policy) {
        return new QueryPolicyRegistration(entityType, policy);
    }
}
