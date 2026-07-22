package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/** Negates one filter expression while preserving explicit boolean precedence. */
record Negation(FilterExpression expression) implements FilterExpression {

    Negation {
        expression = Objects.requireNonNull(expression, "expression must not be null");
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.not(expression);
    }
}
