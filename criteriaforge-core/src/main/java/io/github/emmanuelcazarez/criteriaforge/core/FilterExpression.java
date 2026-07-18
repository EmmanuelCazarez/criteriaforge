package io.github.emmanuelcazarez.criteriaforge.core;

/** A node in an immutable boolean filter expression tree. */
public sealed interface FilterExpression permits Condition, FilterGroup, Negation {
}
