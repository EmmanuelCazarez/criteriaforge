package io.github.emmanuelcazarez.criteriaforge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.PageSpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.SortSpec;
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

        var expected = QuerySpec.builder()
            .select("reference", "customer.name", "total")
            .where(Filters.and(
                Filters.eq("status", "PAID"),
                Filters.gte("total", "100.00"),
                Filters.like("reference", "WEB-%")))
            .sort(SortSpec.desc("total"), SortSpec.asc("reference"))
            .page(PageSpec.offset(10, 20))
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

        assertThat(parsed.filter()).contains(Filters.and(
            Filters.in("status", "PAID", "CREATED", "CANCELLED"),
            Filters.in("country", "MX", "US")));
    }

    @Test
    void givesOrPrefixExplicitFlatPrecedence() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("status_eq", "PAID");
        parameters.add("country_eq", "MX");
        parameters.add("OR_priority_eq", "HIGH");
        parameters.add("OR_reference_like", "VIP-%");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(Filters.or(
            Filters.and(
                Filters.eq("status", "PAID"),
                Filters.eq("country", "MX")),
            Filters.eq("priority", "HIGH"),
            Filters.like("reference", "VIP-%")));
    }

    @Test
    void recognizesLongestSuffixAndNullOperators() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("total_gte", "10");
        parameters.add("createdAt_lte", "2026-07-18T23:59:59");
        parameters.add("cancelledAt_isnull", "true");

        var parsed = parser.parse(parameters);

        assertThat(parsed.filter()).contains(Filters.and(
            Filters.gte("total", "10"),
            Filters.lte("createdAt", "2026-07-18T23:59:59"),
            Filters.isNull("cancelledAt")));
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
}
