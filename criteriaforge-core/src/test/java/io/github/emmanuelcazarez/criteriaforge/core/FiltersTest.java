package io.github.emmanuelcazarez.criteriaforge.core;

import static io.github.emmanuelcazarez.criteriaforge.core.Filters.field;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiltersTest {

    @Test
    void buildsTypedConditionsWithFluentBooleanComposition() {
        var minimumTotal = new BigDecimal("100.00");
        var filter = field("status").eq(Status.PAID)
            .and(field("total").gte(minimumTotal))
            .and(
                field("customer.country").eq("MX")
                    .or(field("customer.country").eq("US")))
            .and(field("cancelledAt").isNull());

        assertThat(filter.accept(new RenderingVisitor()))
            .isEqualTo(
                "(status EQ [PAID] AND total GTE [100.00] AND "
                    + "(customer.country EQ [MX] OR customer.country EQ [US]) "
                    + "AND cancelledAt IS_NULL [])");
    }

    @Test
    void composesDynamicallyCollectedExpressionsAndMembershipValues() {
        var expressions = new ArrayList<FilterExpression>();
        expressions.add(field("status").in(List.of(Status.PAID)));
        expressions.add(field("total").gte(new BigDecimal("50")));

        var filter = Filters.allOf(expressions);

        assertThat(filter.accept(new RenderingVisitor()))
            .isEqualTo("(status IN [PAID] AND total GTE [50])");
    }

    private enum Status {
        PAID
    }

    private static final class RenderingVisitor implements FilterExpression.Visitor<String> {
        @Override
        public String condition(String field, Operator operator, List<Object> values) {
            return field + " " + operator + " " + values;
        }

        @Override
        public String and(List<FilterExpression> expressions) {
            return binary("AND", expressions);
        }

        @Override
        public String or(List<FilterExpression> expressions) {
            return binary("OR", expressions);
        }

        @Override
        public String not(FilterExpression expression) {
            return "NOT(" + expression.accept(this) + ")";
        }

        private String binary(String operator, List<FilterExpression> expressions) {
            return expressions.stream()
                .map(expression -> expression.accept(this))
                .collect(java.util.stream.Collectors.joining(
                    " " + operator + " ", "(", ")"));
        }
    }
}
