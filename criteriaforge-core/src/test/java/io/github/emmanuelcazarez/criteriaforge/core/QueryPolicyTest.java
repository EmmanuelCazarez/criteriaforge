package io.github.emmanuelcazarez.criteriaforge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QueryPolicyTest {

    private final QueryComplexityValidator validator = new QueryComplexityValidator();

    @Test
    void defaultsProtectPublicQueryEndpoints() {
        var policy = QueryPolicy.defaults();

        assertThat(policy.maxPageSize()).isEqualTo(100);
        assertThat(policy.maxConditions()).isEqualTo(25);
        assertThat(policy.maxDepth()).isEqualTo(2);
        assertThat(policy.relationshipTraversal()).isFalse();
        assertThat(policy.allowedFields()).isEmpty();
    }

    @Test
    void defaultPolicyRejectsOversizedPages() {
        var query = QueryRequest.builder().limit(101).build();

        assertThatThrownBy(() -> validator.validate(query, QueryPolicy.defaults()))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.PAGE_SIZE_EXCEEDED);
                assertThat(error.path()).contains("limit");
            });
    }

    @Test
    void countsConditionsAcrossNestedGroupsAndNegations() {
        var query = QueryRequest.builder().where(
            Filters.field("a").eq("1")
                .and(Filters.field("b").eq("2")
                    .or(Filters.field("c").eq("3"))
                    .not()))
            .build();
        var policy = QueryPolicy.builder().maxConditions(2).build();

        assertThatThrownBy(() -> validator.validate(query, policy))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.CONDITION_LIMIT_EXCEEDED));
    }

    @Test
    void policyCollectionsAreImmutableAndDeniedFieldsWin() {
        var policy = QueryPolicy.builder()
            .allowFields("id", "email")
            .denyFields("email")
            .allowOperators("id", Operator.EQ, Operator.IN)
            .relationshipTraversal(true)
            .build();

        assertThat(policy.isFieldAllowed("id")).isTrue();
        assertThat(policy.isFieldAllowed("email")).isFalse();
        assertThat(policy.isOperatorAllowed("id", Operator.IN)).isTrue();
        assertThat(policy.isOperatorAllowed("id", Operator.LIKE)).isFalse();
        assertThatThrownBy(() -> policy.allowedFields().add("secret"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void exposesStablePublicNamesAndResolvesThemToPersistentPaths() {
        var policy = QueryPolicy.builder()
            .allowFields("id", "status")
            .alias("amount", "total")
            .alias("customerName", "customer.name")
            .allowOperators("amount", Operator.EQ, Operator.GTE)
            .build();

        assertThat(policy.isFieldAllowed("amount")).isTrue();
        assertThat(policy.resolveField("amount")).isEqualTo("total");
        assertThat(policy.resolveField("customerName")).isEqualTo("customer.name");
        assertThat(policy.resolveField("status")).isEqualTo("status");
        assertThat(policy.isOperatorAllowed("amount", Operator.GTE)).isTrue();
        assertThat(policy.isOperatorAllowed("amount", Operator.LIKE)).isFalse();
        assertThat(policy.aliases()).containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("amount", "total", "customerName", "customer.name"));
    }
}
