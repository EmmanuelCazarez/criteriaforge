package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/** Enforces transport-neutral query complexity limits before query execution. */
public final class QueryComplexityValidator {

    public void validate(QueryRequest query, QueryPolicy policy) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        query.pagination().ifPresent(pagination -> validatePagination(pagination, policy));
        validateProjectionFields(query, policy);
        validateSortFields(query, policy);
        var conditions = query.filter().map(this::countConditions).orElse(0);
        if (conditions > policy.maxConditions()) {
            throw new QueryValidationException(
                QueryErrorCode.CONDITION_LIMIT_EXCEEDED,
                "Query contains " + conditions + " conditions; maximum is "
                    + policy.maxConditions());
        }
    }

    private static void validatePagination(Pagination pagination, QueryPolicy policy) {
        if (pagination.limit() > policy.maxPageSize()) {
            throw new QueryValidationException(
                QueryErrorCode.PAGE_SIZE_EXCEEDED,
                "Requested limit " + pagination.limit() + " exceeds maximum "
                    + policy.maxPageSize(),
                "limit");
        }
    }

    private static void validateProjectionFields(QueryRequest query, QueryPolicy policy) {
        if (query.fields().size() > policy.maxProjectionFields()) {
            throw new QueryValidationException(
                QueryErrorCode.PROJECTION_LIMIT_EXCEEDED,
                "Query contains " + query.fields().size() + " projection fields; maximum is "
                    + policy.maxProjectionFields(),
                "fields");
        }
    }

    private static void validateSortFields(QueryRequest query, QueryPolicy policy) {
        if (query.sorting().orders().size() > policy.maxSortFields()) {
            throw new QueryValidationException(
                QueryErrorCode.SORT_LIMIT_EXCEEDED,
                "Query contains " + query.sorting().orders().size() + " sort fields; maximum is "
                    + policy.maxSortFields(),
                "sort");
        }
    }

    private int countConditions(FilterExpression expression) {
        if (expression instanceof Condition) {
            return 1;
        }
        if (expression instanceof Negation negation) {
            return countConditions(negation.expression());
        }
        var group = (FilterGroup) expression;
        return group.children().stream().mapToInt(this::countConditions).sum();
    }
}
