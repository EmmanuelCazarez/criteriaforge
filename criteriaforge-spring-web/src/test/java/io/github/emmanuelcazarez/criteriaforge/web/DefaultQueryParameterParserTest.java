package io.github.emmanuelcazarez.criteriaforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.ProjectionField;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class DefaultQueryParameterParserTest {

    private final QueryParameterParser parser = new DefaultQueryParameterParser();

    @Test
    void parsesFiltersFieldsSortAndPagination() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("status_eq", "PAID");
        parameters.add("total_gte", "100.00");
        parameters.add("reference_like", "WEB-%");
        parameters.add("fields", "reference,customer.name,total");
        parameters.add("sort", "-total,reference");
        parameters.add("offset", "10");
        parameters.add("limit", "20");

        var parsed = parser.parse(parameters);

        var expected = QueryRequest.builder()
            .select("reference", "customer.name", "total")
            .where(Filters.field("status").eq("PAID")
                .and(Filters.field("total").gte("100.00"))
                .and(Filters.field("reference").like("WEB-%")))
            .orderByDescending("total")
            .orderByAscending("reference")
            .offset(10)
            .limit(20)
            .build();
        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    void supportsRepeatedAndCommaSeparatedInValues() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("status_in", "PAID,CREATED");
        parameters.add("status_in", "CANCELLED");
        parameters.add("country", "MX,US");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(Filters.field("status")
            .in("PAID", "CREATED", "CANCELLED")
            .and(Filters.field("country").in("MX", "US")));
    }

    @Test
    void givesOrPrefixExplicitFlatPrecedence() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("status_eq", "PAID");
        parameters.add("country_eq", "MX");
        parameters.add("OR_priority_eq", "HIGH");
        parameters.add("OR_reference_like", "VIP-%");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(Filters.anyOf(
            Filters.field("status").eq("PAID")
                .and(Filters.field("country").eq("MX")),
            Filters.field("priority").eq("HIGH"),
            Filters.field("reference").like("VIP-%")));
    }

    @Test
    void recognizesLongestSuffixAndNullOperators() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("total_gte", "10");
        parameters.add("createdAt_lte", "2026-07-18T23:59:59");
        parameters.add("cancelledAt_isnull", "true");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(Filters.field("total").gte("10")
            .and(Filters.field("createdAt").lte("2026-07-18T23:59:59"))
            .and(Filters.field("cancelledAt").isNull()));
    }

    @Test
    void rejectsUnknownOperatorSuffixesAndMalformedControlValues() {
        var unknown = new LinkedMultiValueMap<String, String>();
        unknown.add("total_approx", "10");
        assertThatThrownBy(() -> parser.parse(unknown))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.UNSUPPORTED_OPERATOR));

        var missingLimit = new LinkedMultiValueMap<String, String>();
        missingLimit.add("offset", "10");
        assertThatThrownBy(() -> parser.parse(missingLimit))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.MALFORMED_QUERY));

        var duplicateLimit = new LinkedMultiValueMap<String, String>();
        duplicateLimit.add("limit", "10");
        duplicateLimit.add("limit", "20");
        assertThatThrownBy(() -> parser.parse(duplicateLimit))
            .isInstanceOf(QueryValidationException.class);
    }

    @Test
    void parsesReadableNestedExpressionsAndPerRequestProjectionAliases() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add(
            "filter",
            "status == PAID and "
                + "(total >= 100 or customer.country in (\"MX\", \"US\")) "
                + "and cancelledAt is null");
        parameters.add(
            "fields",
            "id,customerName as buyer.name,amount as orderTotal");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(
            Filters.field("status").eq("PAID")
                .and(Filters.field("total").gte("100")
                    .or(Filters.field("customer.country").in("MX", "US")))
                .and(Filters.field("cancelledAt").isNull()));
        assertThat(parsed.fields()).containsExactly(
            ProjectionField.of("id"),
            ProjectionField.aliased("customerName", "buyer.name"),
            ProjectionField.aliased("amount", "orderTotal"));
    }

    @Test
    void appliesNotComparisonAndOrPrecedence() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add(
            "filter",
            "status == PAID or total >= 100 and not cancelledAt is null");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(
            Filters.field("status").eq("PAID")
                .or(Filters.field("total").gte("100")
                    .and(Filters.field("cancelledAt").isNull().not())));
    }

    @Test
    void parsesTheCompleteReadableOperatorSet() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add(
            "filter",
            "reference like \"VIP-%\" and status != CANCELLED "
                + "and total > 10 and total <= 20 and score < 5 and score >= 1 "
                + "and createdAt between \"2026-01-01\" and \"2026-12-31\" "
                + "and country in (\"MX\", \"US\") and cancelledAt is not null");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(Filters.allOf(
            Filters.field("reference").like("VIP-%"),
            Filters.field("status").ne("CANCELLED"),
            Filters.field("total").gt("10"),
            Filters.field("total").lte("20"),
            Filters.field("score").lt("5"),
            Filters.field("score").gte("1"),
            Filters.field("createdAt").between("2026-01-01", "2026-12-31"),
            Filters.field("country").in("MX", "US"),
            Filters.field("cancelledAt").isNotNull()));
    }

    @Test
    void rejectsMixedSyntaxRepeatedExpressionsAndConfiguredLimits() {
        var mixed = new LinkedMultiValueMap<String, String>();
        mixed.add("filter", "status == PAID");
        mixed.add("total_gte", "100");
        assertThatThrownBy(() -> parser.parse(mixed))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.MALFORMED_QUERY));

        var repeated = new LinkedMultiValueMap<String, String>();
        repeated.add("filter", "status == PAID");
        repeated.add("filter", "status == CREATED");
        assertThatThrownBy(() -> parser.parse(repeated))
            .isInstanceOf(QueryValidationException.class);

        var limitedParser = new DefaultQueryParameterParser(24, 2);
        var tooLong = new LinkedMultiValueMap<String, String>();
        tooLong.add("filter", "status == A_VERY_LONG_VALUE");
        assertThatThrownBy(() -> limitedParser.parse(tooLong))
            .isInstanceOf(QueryValidationException.class);

        var tooDeep = new LinkedMultiValueMap<String, String>();
        tooDeep.add("filter", "(((status == PAID)))");
        assertThatThrownBy(() -> limitedParser.parse(tooDeep))
            .isInstanceOf(QueryValidationException.class);
    }
}
