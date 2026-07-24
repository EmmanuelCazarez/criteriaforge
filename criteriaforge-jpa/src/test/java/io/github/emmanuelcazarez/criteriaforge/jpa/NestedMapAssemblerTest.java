package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.ProjectionField;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NestedMapAssemblerTest {

    private final NestedMapAssembler assembler = new NestedMapAssembler();

    @Test
    void assemblesNestedMapsWithoutDroppingSiblingFields() {
        var row = assembler.assemble(
            List.of(
                ProjectionField.of("id"),
                ProjectionField.of("customer.name"),
                ProjectionField.of("customer.country"),
                ProjectionField.of("total")),
            List.of(7L, "Ana", "MX", new BigDecimal("125.00")));

        assertThat(row)
            .containsEntry("id", 7L)
            .containsEntry("total", new BigDecimal("125.00"));
        assertThat((Map<String, Object>) row.get("customer"))
            .containsEntry("name", "Ana")
            .containsEntry("country", "MX");
    }

    @Test
    void keepsNullLeafValuesAndReturnsDeeplyImmutableMaps() {
        var row = assembler.assemble(
            List.of(
                ProjectionField.of("customer.name"),
                ProjectionField.of("customer.country")),
            Arrays.asList(null, "MX"));

        var customer = (Map<String, Object>) row.get("customer");
        assertThat(customer).containsEntry("name", null).containsEntry("country", "MX");
        assertThatThrownBy(() -> row.put("other", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> customer.put("other", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMismatchedColumnsAndPathCollisions() {
        assertThatThrownBy(() -> assembler.assemble(
            List.of(ProjectionField.of("id")), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(
            List.of(
                ProjectionField.of("customer"),
                ProjectionField.of("customer.name")),
            List.of("raw", "Ana")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesPerRequestOutputAliasesWithoutChangingTheSelectedSource() {
        var row = assembler.assemble(
            List.of(
                ProjectionField.aliased("customer.name", "buyer.name"),
                ProjectionField.aliased("total", "orderTotal")),
            List.of("Ana", new BigDecimal("125.00")));

        assertThat((Map<String, Object>) row.get("buyer"))
            .containsEntry("name", "Ana");
        assertThat(row).containsEntry("orderTotal", new BigDecimal("125.00"));
    }
}
