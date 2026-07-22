package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Arrays;
import java.util.List;

/** Factory methods for building readable filter expression trees. */
public final class Filters {

    private Filters() {
    }

    public static FilterExpression and(FilterExpression... expressions) {
        return new FilterGroup(FilterGroup.Junction.AND, List.of(expressions));
    }

    public static FilterExpression or(FilterExpression... expressions) {
        return new FilterGroup(FilterGroup.Junction.OR, List.of(expressions));
    }

    public static FilterExpression not(FilterExpression expression) {
        return new Negation(expression);
    }

    public static FilterExpression eq(String field, String value) {
        return condition(field, Operator.EQ, value);
    }

    public static FilterExpression ne(String field, String value) {
        return condition(field, Operator.NE, value);
    }

    public static FilterExpression gt(String field, String value) {
        return condition(field, Operator.GT, value);
    }

    public static FilterExpression gte(String field, String value) {
        return condition(field, Operator.GTE, value);
    }

    public static FilterExpression lt(String field, String value) {
        return condition(field, Operator.LT, value);
    }

    public static FilterExpression lte(String field, String value) {
        return condition(field, Operator.LTE, value);
    }

    public static FilterExpression like(String field, String value) {
        return condition(field, Operator.LIKE, value);
    }

    public static FilterExpression in(String field, String... values) {
        return condition(field, Operator.IN, values);
    }

    public static FilterExpression between(String field, String lower, String upper) {
        return condition(field, Operator.BETWEEN, lower, upper);
    }

    public static FilterExpression isNull(String field) {
        return condition(field, Operator.IS_NULL);
    }

    public static FilterExpression isNotNull(String field) {
        return condition(field, Operator.IS_NOT_NULL);
    }

    public static FilterExpression condition(String field, Operator operator, String... values) {
        return new Condition(field, operator, Arrays.asList(values));
    }
}
