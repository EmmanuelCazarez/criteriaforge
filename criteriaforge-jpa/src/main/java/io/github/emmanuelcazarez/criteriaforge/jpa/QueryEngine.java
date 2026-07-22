package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;

/** Executes validated dynamic queries against JPA-managed entity types. */
public interface QueryEngine {

    QueryResult<?> execute(Class<?> entityType, QueryRequest query);
}
