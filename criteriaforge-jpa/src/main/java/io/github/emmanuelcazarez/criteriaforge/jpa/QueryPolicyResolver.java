package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;

/** Resolves the effective query policy for a JPA entity type. */
@FunctionalInterface
public interface QueryPolicyResolver {

    QueryPolicy resolve(Class<?> entityType);
}
