package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;

/** Supplies the effective query policy for a JPA entity type. */
@FunctionalInterface
public interface QueryPolicyProvider {

    QueryPolicy policyFor(Class<?> entityType);
}
