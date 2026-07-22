package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Converts transport string values into types resolved from the JPA metamodel. */
final class JpaValueConverter {

    public Object convert(String raw, Class<?> targetType) {
        Objects.requireNonNull(raw, "raw value must not be null");
        Objects.requireNonNull(targetType, "target type must not be null");
        try {
            return convertKnownType(raw, boxed(targetType));
        } catch (QueryValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw conversionFailure(targetType, exception);
        }
    }

    public List<?> convertAll(List<String> rawValues, Class<?> targetType) {
        Objects.requireNonNull(rawValues, "raw values must not be null");
        return rawValues.stream().map(raw -> convert(raw, targetType)).toList();
    }

    private Object convertKnownType(String raw, Class<?> targetType) {
        if (targetType == String.class || targetType == CharSequence.class) {
            return raw;
        }
        if (targetType == Character.class) {
            if (raw.length() != 1) {
                throw conversionFailure(targetType, null);
            }
            return raw.charAt(0);
        }
        if (targetType == Byte.class) {
            return Byte.valueOf(raw);
        }
        if (targetType == Short.class) {
            return Short.valueOf(raw);
        }
        if (targetType == Integer.class) {
            return Integer.valueOf(raw);
        }
        if (targetType == Long.class) {
            return Long.valueOf(raw);
        }
        if (targetType == Float.class) {
            return Float.valueOf(raw);
        }
        if (targetType == Double.class) {
            return Double.valueOf(raw);
        }
        if (targetType == BigInteger.class) {
            return new BigInteger(raw);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(raw);
        }
        if (targetType == Boolean.class) {
            return parseBoolean(raw);
        }
        if (targetType == UUID.class) {
            return UUID.fromString(raw);
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(raw);
        }
        if (targetType == LocalTime.class) {
            return LocalTime.parse(raw);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(raw);
        }
        if (targetType == OffsetDateTime.class) {
            return OffsetDateTime.parse(raw);
        }
        if (targetType == Instant.class) {
            return Instant.parse(raw);
        }
        if (targetType.isEnum()) {
            return parseEnum(raw, targetType);
        }
        throw conversionFailure(targetType, null);
    }

    private static Boolean parseBoolean(String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalArgumentException("not a boolean");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object parseEnum(String raw, Class<?> targetType) {
        var enumType = (Class<? extends Enum>) targetType;
        try {
            return Enum.valueOf(enumType, raw);
        } catch (IllegalArgumentException ignored) {
            var matches = Arrays.stream(enumType.getEnumConstants())
                .filter(value -> value.name().toLowerCase(Locale.ROOT)
                    .equals(raw.toLowerCase(Locale.ROOT)))
                .toList();
            if (matches.size() == 1) {
                return matches.get(0);
            }
            throw ignored;
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

    private static QueryValidationException conversionFailure(
            Class<?> targetType, Throwable cause) {
        return new QueryValidationException(
            QueryErrorCode.VALUE_CONVERSION_FAILED,
            "Value cannot be converted to " + targetType.getSimpleName(),
            null,
            cause);
    }
}
