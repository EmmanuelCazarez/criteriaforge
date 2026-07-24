package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Factory methods for building readable filter expression trees. */
public final class Filters {

    private Filters() {
    }

    /** Starts a fluent filter condition for a persistent or public field path. */
    public static FilterField field(String path) {
        return new FilterField(path);
    }

    public static FilterExpression allOf(FilterExpression... expressions) {
        return group(FilterGroup.Junction.AND, Arrays.asList(expressions));
    }

    public static FilterExpression allOf(Collection<? extends FilterExpression> expressions) {
        return group(FilterGroup.Junction.AND, expressions);
    }

    public static FilterExpression anyOf(FilterExpression... expressions) {
        return group(FilterGroup.Junction.OR, Arrays.asList(expressions));
    }

    public static FilterExpression anyOf(Collection<? extends FilterExpression> expressions) {
        return group(FilterGroup.Junction.OR, expressions);
    }

    public static FilterExpression not(FilterExpression expression) {
        return new Negation(expression);
    }

    public static FilterExpression condition(String field, Operator operator, Object... values) {
        return new Condition(field, operator, Arrays.asList(values));
    }

    private static FilterExpression group(
            FilterGroup.Junction junction,
            Collection<? extends FilterExpression> expressions) {
        Objects.requireNonNull(expressions, "expressions must not be null");
        List<FilterExpression> copy = expressions.stream()
            .map(expression -> Objects.requireNonNull(
                expression, "expressions must not contain null"))
            .flatMap(expression -> expression instanceof FilterGroup group
                    && group.junction() == junction
                ? group.children().stream()
                : java.util.stream.Stream.of(expression))
            .toList();
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("at least one filter expression is required");
        }
        return copy.size() == 1 ? copy.get(0) : new FilterGroup(junction, copy);
    }
}
