package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Collection;
import java.util.Objects;

/** Fluent entry point for creating typed conditions against one field path. */
public final class FilterField {
    private final String path;

    FilterField(String path) {
        this.path = QueryPath.requireValid(path, "field");
    }

    public FilterExpression eq(Object value) {
        return condition(Operator.EQ, value);
    }

    public FilterExpression ne(Object value) {
        return condition(Operator.NE, value);
    }

    public FilterExpression gt(Object value) {
        return condition(Operator.GT, value);
    }

    public FilterExpression gte(Object value) {
        return condition(Operator.GTE, value);
    }

    public FilterExpression lt(Object value) {
        return condition(Operator.LT, value);
    }

    public FilterExpression lte(Object value) {
        return condition(Operator.LTE, value);
    }

    public FilterExpression like(Object value) {
        return condition(Operator.LIKE, value);
    }

    public FilterExpression in(Object... values) {
        return Filters.condition(path, Operator.IN, values);
    }

    public FilterExpression in(Collection<?> values) {
        Objects.requireNonNull(values, "values must not be null");
        return in(values.toArray());
    }

    public FilterExpression between(Object lower, Object upper) {
        return Filters.condition(path, Operator.BETWEEN, lower, upper);
    }

    public FilterExpression isNull() {
        return Filters.condition(path, Operator.IS_NULL);
    }

    public FilterExpression isNotNull() {
        return Filters.condition(path, Operator.IS_NOT_NULL);
    }

    private FilterExpression condition(Operator operator, Object value) {
        return Filters.condition(
            path, operator, Objects.requireNonNull(value, "value must not be null"));
    }
}
