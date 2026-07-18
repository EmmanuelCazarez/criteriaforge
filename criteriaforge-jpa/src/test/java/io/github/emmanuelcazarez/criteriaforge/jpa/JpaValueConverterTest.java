package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JpaValueConverterTest {

    private final JpaValueConverter converter = new JpaValueConverter();

    @ParameterizedTest
    @MethodSource("validValues")
    void convertsToTheResolvedJpaType(String raw, Class<?> type, Object expected) {
        assertThat(converter.convert(raw, type)).isEqualTo(expected);
    }

    @Test
    void convertsEnumsExactlyOrCaseInsensitively() {
        assertThat(converter.convert("PAID", State.class)).isEqualTo(State.PAID);
        assertThat(converter.convert("cancelled", State.class)).isEqualTo(State.CANCELLED);
    }

    @Test
    void rejectsInvalidValuesWithAStableError() {
        assertThatThrownBy(() -> converter.convert("not-a-number", BigDecimal.class))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.VALUE_CONVERSION_FAILED);
                assertThat(error.getMessage()).contains("BigDecimal").doesNotContain("SQL");
            });
        assertThatThrownBy(() -> converter.convert("yes", boolean.class))
            .isInstanceOf(QueryValidationException.class);
    }

    private static Stream<Arguments> validValues() {
        var uuid = UUID.fromString("b13eb647-7e25-49d7-9886-a954797f60a7");
        return Stream.of(
            Arguments.of("text", String.class, "text"),
            Arguments.of("Z", char.class, 'Z'),
            Arguments.of("12", byte.class, (byte) 12),
            Arguments.of("32000", short.class, (short) 32000),
            Arguments.of("42", int.class, 42),
            Arguments.of("9000000000", long.class, 9_000_000_000L),
            Arguments.of("1.25", float.class, 1.25F),
            Arguments.of("2.5", double.class, 2.5D),
            Arguments.of("999999999999999999", BigInteger.class,
                new BigInteger("999999999999999999")),
            Arguments.of("125.50", BigDecimal.class, new BigDecimal("125.50")),
            Arguments.of("true", boolean.class, true),
            Arguments.of(uuid.toString(), UUID.class, uuid),
            Arguments.of("2026-07-18", LocalDate.class, LocalDate.of(2026, 7, 18)),
            Arguments.of("15:45:30", LocalTime.class, LocalTime.of(15, 45, 30)),
            Arguments.of("2026-07-18T15:45:30", LocalDateTime.class,
                LocalDateTime.of(2026, 7, 18, 15, 45, 30)),
            Arguments.of("2026-07-18T15:45:30-07:00", OffsetDateTime.class,
                OffsetDateTime.of(2026, 7, 18, 15, 45, 30, 0, ZoneOffset.ofHours(-7))),
            Arguments.of("2026-07-18T22:45:30Z", Instant.class,
                Instant.parse("2026-07-18T22:45:30Z")));
    }

    private enum State {
        PAID,
        CANCELLED
    }
}
