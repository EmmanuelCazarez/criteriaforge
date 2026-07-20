package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/** Enforces transport-neutral query complexity limits before query execution. */
public final class QueryComplexityValidator {

    public void validate(QuerySpec query, QueryPolicy policy) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        query.page().ifPresent(page -> validatePage(page, policy));
        var conditions = query.filter().map(this::countConditions).orElse(0);
        if (conditions > policy.maxConditions()) {
            throw new QueryValidationException(
                QueryErrorCode.CONDITION_LIMIT_EXCEEDED,
                "Query contains " + conditions + " conditions; maximum is "
                    + policy.maxConditions());
        }
    }

    private static void validatePage(PageSpec page, QueryPolicy policy) {
        if (page.limit() > policy.maxPageSize()) {
            throw new QueryValidationException(
                QueryErrorCode.PAGE_SIZE_EXCEEDED,
                "Requested limit " + page.limit() + " exceeds maximum "
                    + policy.maxPageSize(),
                "limit");
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
