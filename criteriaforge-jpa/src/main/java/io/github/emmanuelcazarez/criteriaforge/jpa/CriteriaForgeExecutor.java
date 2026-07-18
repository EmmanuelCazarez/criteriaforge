package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;

/** Executes validated dynamic queries against JPA-managed entity types. */
public interface CriteriaForgeExecutor {

    <T> QueryResult<T> findAll(Class<T> entityType, QuerySpec query);
}
