package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.Operator;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import java.util.Objects;

/** Validates that an operator has meaningful Criteria semantics for a Java type. */
final class OperatorCompatibility {

    void validate(Operator operator, Class<?> javaType, String field) {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(javaType, "javaType must not be null");
        var compatible = switch (operator) {
            case LIKE -> CharSequence.class.isAssignableFrom(boxed(javaType));
            case GT, GTE, LT, LTE, BETWEEN ->
                Comparable.class.isAssignableFrom(boxed(javaType));
            default -> true;
        };
        if (!compatible) {
            throw new QueryValidationException(
                QueryErrorCode.INCOMPATIBLE_OPERATOR,
                "Operator " + operator + " is incompatible with " + javaType.getSimpleName(),
                field);
        }
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
