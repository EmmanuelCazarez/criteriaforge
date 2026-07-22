package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;

/** An opaque, immutable boolean filter expression created through {@link Filters}. */
public sealed interface FilterExpression permits Condition, FilterGroup, Negation {

    /** Allows persistence adapters to interpret expressions without exposing their implementations. */
    <T> T accept(Visitor<T> visitor);

    /** Structural operations needed by CriteriaForge persistence adapters. */
    interface Visitor<T> {
        T condition(String field, Operator operator, List<String> values);

        T and(List<FilterExpression> expressions);

        T or(List<FilterExpression> expressions);

        T not(FilterExpression expression);
    }
}
