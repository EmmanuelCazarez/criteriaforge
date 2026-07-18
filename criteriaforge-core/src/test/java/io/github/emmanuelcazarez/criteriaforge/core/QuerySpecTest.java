package io.github.emmanuelcazarez.criteriaforge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuerySpecTest {

    @Test
    void buildsANestedQueryWithoutSharingMutableCollections() {
        var fields = new ArrayList<>(List.of("id", "customer.name"));
        var query = QuerySpec.builder()
            .select(fields)
            .where(Filters.and(
                Filters.eq("status", "PAID"),
                Filters.or(
                    Filters.gte("total", "100.00"),
                    Filters.isNull("cancelledAt"))))
            .sort(SortSpec.desc("createdAt"))
            .page(PageSpec.offset(0, 20))
            .build();

        fields.add("secret");

        assertThat(query.fields()).containsExactly("id", "customer.name");
        assertThat(query.page()).contains(PageSpec.offset(0, 20));
        assertThat(query.filter()).containsInstanceOf(FilterGroup.class);
        assertThat(query.sorts()).containsExactly(SortSpec.desc("createdAt"));
        assertThatThrownBy(() -> query.fields().add("other"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankPathsAndWrongOperatorArity() {
        assertThatIllegalArgumentException().isThrownBy(() -> Filters.eq(" ", "x"));
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
        assertThatIllegalArgumentException().isThrownBy(() -> QuerySpec.builder()
            .select("id", "id")
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
