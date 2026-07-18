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

    public static Condition eq(String field, String value) {
        return condition(field, Operator.EQ, value);
    }

    public static Condition ne(String field, String value) {
        return condition(field, Operator.NE, value);
    }

    public static Condition gt(String field, String value) {
        return condition(field, Operator.GT, value);
    }

    public static Condition gte(String field, String value) {
        return condition(field, Operator.GTE, value);
    }

    public static Condition lt(String field, String value) {
        return condition(field, Operator.LT, value);
    }

    public static Condition lte(String field, String value) {
        return condition(field, Operator.LTE, value);
    }

    public static Condition like(String field, String value) {
        return condition(field, Operator.LIKE, value);
    }

    public static Condition in(String field, String... values) {
        return condition(field, Operator.IN, values);
    }

    public static Condition between(String field, String lower, String upper) {
        return condition(field, Operator.BETWEEN, lower, upper);
    }

    public static Condition isNull(String field) {
        return condition(field, Operator.IS_NULL);
    }

    public static Condition isNotNull(String field) {
        return condition(field, Operator.IS_NOT_NULL);
    }

    private static Condition condition(String field, Operator operator, String... values) {
        return new Condition(field, operator, Arrays.asList(values));
    }
}
