package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;

/** An opaque, immutable boolean filter expression created through {@link Filters}. */
public sealed interface FilterExpression permits Condition, FilterGroup, Negation {

    /** Returns an immutable conjunction of this expression and {@code other}. */
    default FilterExpression and(FilterExpression other) {
        return Filters.allOf(this, other);
    }

    /** Returns an immutable disjunction of this expression and {@code other}. */
    default FilterExpression or(FilterExpression other) {
        return Filters.anyOf(this, other);
    }

    /** Returns an immutable negation of this expression. */
    default FilterExpression not() {
        return Filters.not(this);
    }

    /** Allows persistence adapters to interpret expressions without exposing their implementations. */
    <T> T accept(Visitor<T> visitor);

    /** Structural operations needed by CriteriaForge persistence adapters. */
    interface Visitor<T> {
        T condition(String field, Operator operator, List<Object> values);

        T and(List<FilterExpression> expressions);

        T or(List<FilterExpression> expressions);

        T not(FilterExpression expression);
    }
}
