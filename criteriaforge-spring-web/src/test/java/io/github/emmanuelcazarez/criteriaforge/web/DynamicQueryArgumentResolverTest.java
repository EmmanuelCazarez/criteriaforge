package io.github.emmanuelcazarez.criteriaforge.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.ProjectionField;
import io.github.emmanuelcazarez.criteriaforge.web.annotation.DynamicQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class DynamicQueryArgumentResolverTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QueryController())
            .setCustomArgumentResolvers(
                new DynamicQueryArgumentResolver(new DefaultQueryParameterParser()))
            .build();
    }

    @Test
    void bindsAnnotatedQueryRequestParameters() throws Exception {
        mockMvc.perform(get("/orders")
                .queryParam("status_eq", "PAID")
                .queryParam("fields", "reference,total")
                .queryParam("sort", "-total")
                .queryParam("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                "[reference, total]|[Order[field=total, direction=DESC]]|20"));
    }

    @Test
    void propagatesStableParserFailures() {
        assertThatThrownBy(() -> mockMvc.perform(get("/orders")
                .queryParam("total_approx", "10")))
            .hasRootCauseInstanceOf(QueryValidationException.class);
    }

    @RestController
    static class QueryController {
        @GetMapping("/orders")
        String orders(@DynamicQuery QueryRequest query) {
            return query.fields().stream().map(ProjectionField::output).toList()
                + "|" + query.sorting().orders() + "|"
                + query.pagination().orElseThrow().limit();
        }
    }
}
