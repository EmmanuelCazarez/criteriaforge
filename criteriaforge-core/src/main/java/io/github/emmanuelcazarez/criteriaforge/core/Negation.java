package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/** Negates one filter expression while preserving explicit boolean precedence. */
public record Negation(FilterExpression expression) implements FilterExpression {

    public Negation {
        expression = Objects.requireNonNull(expression, "expression must not be null");
    }
}
