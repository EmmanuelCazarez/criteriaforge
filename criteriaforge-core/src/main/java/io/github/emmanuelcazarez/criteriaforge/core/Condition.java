package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;
import java.util.Objects;

/** A leaf filter that applies one operator to one persistent field path. */
record Condition(String field, Operator operator, List<String> values)
        implements FilterExpression {

    Condition {
        field = QueryPath.requireValid(field, "field");
        operator = Objects.requireNonNull(operator, "operator must not be null");
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("condition values must not contain null");
        }
        validateArity(operator, values.size());
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.condition(field, operator, values);
    }

    private static void validateArity(Operator operator, int valueCount) {
        var valid = switch (operator) {
            case IS_NULL, IS_NOT_NULL -> valueCount == 0;
            case BETWEEN -> valueCount == 2;
            case IN -> valueCount > 0;
            default -> valueCount == 1;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                "operator " + operator + " does not accept " + valueCount + " values");
        }
    }
}
