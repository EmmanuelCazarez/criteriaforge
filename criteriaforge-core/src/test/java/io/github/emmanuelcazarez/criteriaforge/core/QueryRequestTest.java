package io.github.emmanuelcazarez.criteriaforge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryRequestTest {

    @Test
    void buildsANestedQueryWithoutSharingMutableCollections() {
        var fields = new ArrayList<>(List.of("id", "customer.name"));
        var query = QueryRequest.builder()
            .select(fields)
            .where(Filters.field("status").eq("PAID")
                .and(Filters.field("total").gte("100.00")
                    .or(Filters.field("cancelledAt").isNull())))
            .sort(SortSpec.desc("createdAt"))
            .page(PageSpec.offset(0, 20))
            .build();

        fields.add("secret");

        assertThat(query.fields()).containsExactly(
            ProjectionField.of("id"),
            ProjectionField.of("customer.name"));
        assertThat(query.page()).contains(PageSpec.offset(0, 20));
        assertThat(query.filter()).containsInstanceOf(FilterGroup.class);
        assertThat(query.sorts()).containsExactly(SortSpec.desc("createdAt"));
        assertThatThrownBy(() -> query.fields().add(ProjectionField.of("other")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankPathsAndWrongOperatorArity() {
        assertThatIllegalArgumentException().isThrownBy(() -> Filters.field(" ").eq("x"));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new Condition("id", Operator.IN, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new Condition("id", Operator.IS_NULL, List.of("x")));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new Condition("total", Operator.BETWEEN, List.of("10")));
    }

    @Test
    void rejectsInvalidPagesAndDuplicateProjectionFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> PageSpec.offset(-1, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> PageSpec.offset(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> QueryRequest.builder()
            .select("id", "id")
            .build());
    }

    @Test
    void keepsProjectionSourcesSeparateFromPerRequestOutputPaths() {
        var query = QueryRequest.builder()
            .select("id")
            .selectAs("customer.name", "buyer.name")
            .selectAs("total", "orderTotal")
            .build();

        assertThat(query.fields()).containsExactly(
            ProjectionField.of("id"),
            ProjectionField.aliased("customer.name", "buyer.name"),
            ProjectionField.aliased("total", "orderTotal"));
    }

    @Test
    void rejectsDuplicateSourcesAndCollidingOutputPaths() {
        assertThatIllegalArgumentException().isThrownBy(() -> QueryRequest.builder()
            .select("total")
            .selectAs("total", "orderTotal")
            .build());
        assertThatIllegalArgumentException().isThrownBy(() -> QueryRequest.builder()
            .selectAs("customer.name", "buyer")
            .selectAs("customer.country", "buyer.country")
            .build());
        assertThatIllegalArgumentException().isThrownBy(() -> QueryRequest.builder()
            .selectAs("customer.name", "buyer.name")
            .selectAs("customer.country", "buyer.name")
            .build());
    }

    @Test
    void queryResultsDefensivelyCopyTheirContent() {
        var content = new ArrayList<>(List.of("first"));
        var result = new QueryResult<>(content, 1, 0, 20);

        content.add("second");

        assertThat(result.content()).containsExactly("first");
        assertThatThrownBy(() -> result.content().add("third"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
