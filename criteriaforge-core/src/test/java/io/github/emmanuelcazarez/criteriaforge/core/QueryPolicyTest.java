package io.github.emmanuelcazarez.criteriaforge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueryPolicyTest {

    private final QueryComplexityValidator validator = new QueryComplexityValidator();

    @Test
    void defaultsProtectPublicQueryEndpoints() {
        var policy = QueryPolicy.defaults();

        assertThat(policy.maxPageSize()).isEqualTo(100);
        assertThat(policy.maxConditions()).isEqualTo(25);
        assertThat(policy.maxDepth()).isEqualTo(2);
        assertThat(policy.maxProjectionFields()).isEqualTo(20);
        assertThat(policy.maxSortFields()).isEqualTo(5);
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

    @Test
    void configuresIndependentProjectionFilterAndSortPermissions() {
        var policy = QueryPolicy.builder()
            .allowProjectionFields("id", "displayName")
            .allowFilterFields("status")
            .allowSortFields("createdAt")
            .denyFields("id")
            .build();

        assertThat(policy.isProjectionAllowed("displayName")).isTrue();
        assertThat(policy.isProjectionAllowed("status")).isFalse();
        assertThat(policy.isFilterAllowed("status")).isTrue();
        assertThat(policy.isFilterAllowed("displayName")).isFalse();
        assertThat(policy.isSortAllowed("createdAt")).isTrue();
        assertThat(policy.isSortAllowed("displayName")).isFalse();
        assertThat(policy.isProjectionAllowed("id")).isFalse();
    }

    @Test
    void anExplicitEmptyOperationAllowlistDeniesThatOperation() {
        var policy = QueryPolicy.builder()
            .allowFilterFields(List.of())
            .build();

        assertThat(policy.isFilterAllowed("status")).isFalse();
        assertThat(policy.isProjectionAllowed("status")).isTrue();
        assertThat(policy.isSortAllowed("status")).isTrue();
    }

    @Test
    void aliasesDoNotGrantOperationPermissions() {
        var policy = QueryPolicy.builder()
            .alias("amount", "total")
            .allowProjectionFields("amount")
            .allowFilterFields("status")
            .allowSortFields("createdAt")
            .build();

        assertThat(policy.isProjectionAllowed("amount")).isTrue();
        assertThat(policy.isFilterAllowed("amount")).isFalse();
        assertThat(policy.isSortAllowed("amount")).isFalse();
        assertThat(policy.resolveField("amount")).isEqualTo("total");
    }

    @Test
    void legacyAllowFieldsAppliesToEveryOperationAndIncludesAliases() {
        var policy = QueryPolicy.builder()
            .allowFields("id")
            .alias("amount", "total")
            .build();

        assertThat(policy.isProjectionAllowed("id")).isTrue();
        assertThat(policy.isFilterAllowed("id")).isTrue();
        assertThat(policy.isSortAllowed("id")).isTrue();
        assertThat(policy.isProjectionAllowed("amount")).isTrue();
        assertThat(policy.allowedFields()).containsExactlyInAnyOrder("id", "amount");
    }

    @Test
    void legacyAliasesRemainVisibleWhenTheUnifiedAllowlistIsEmpty() {
        var policy = QueryPolicy.builder()
            .allowFields(List.of())
            .alias("amount", "total")
            .build();

        assertThat(policy.allowedFields()).containsExactly("amount");
        assertThat(policy.isFieldAllowed("unlisted")).isTrue();
    }

    @Test
    void deprecatedUnifiedChecksRepresentTheCommonOperationPermission() {
        var policy = QueryPolicy.builder()
            .allowProjectionFields("id", "status")
            .allowFilterFields("status")
            .build();

        assertThat(policy.allowedFields()).containsExactly("status");
        assertThat(policy.isFieldAllowed("status")).isTrue();
        assertThat(policy.isFieldAllowed("id")).isFalse();
    }

    @Test
    void rejectsMixingLegacyAndOperationSpecificPermissions() {
        assertThatThrownBy(() -> QueryPolicy.builder()
            .allowFields("id")
            .allowProjectionFields("id"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("unified and operation-specific field permissions must not be mixed");

        assertThatThrownBy(() -> QueryPolicy.builder()
            .allowSortFields("id")
            .allowFields("id"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("unified and operation-specific field permissions must not be mixed");
    }

    @Test
    void rejectsTooManyProjectionFields() {
        var query = QueryRequest.builder().select("id", "status").build();
        var policy = QueryPolicy.builder().maxProjectionFields(1).build();

        assertThatThrownBy(() -> validator.validate(query, policy))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.PROJECTION_LIMIT_EXCEEDED);
                assertThat(error.path()).contains("fields");
            });
    }

    @Test
    void rejectsTooManyRequestedSortFields() {
        var query = QueryRequest.builder()
            .orderByAscending("status")
            .orderByDescending("createdAt")
            .build();
        var policy = QueryPolicy.builder().maxSortFields(1).build();

        assertThatThrownBy(() -> validator.validate(query, policy))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.SORT_LIMIT_EXCEEDED);
                assertThat(error.path()).contains("sort");
            });
    }

    @Test
    void rejectsNonPositiveProjectionAndSortLimits() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryPolicy.builder().maxProjectionFields(0).build())
            .withMessage("maxProjectionFields must be at least one");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryPolicy.builder().maxSortFields(0).build())
            .withMessage("maxSortFields must be at least one");
    }

    @Test
    void configuresAnOptionalTieBreakerOverride() {
        assertThat(QueryPolicy.defaults().tieBreaker()).isEmpty();

        var ascending = QueryPolicy.builder().tieBreaker("reference").build();
        var descending = QueryPolicy.builder()
            .tieBreaker("createdAt", SortDirection.DESC)
            .build();

        assertThat(ascending.tieBreaker())
            .contains(new Sorting.Order("reference", SortDirection.ASC));
        assertThat(descending.tieBreaker())
            .contains(new Sorting.Order("createdAt", SortDirection.DESC));
    }
}
