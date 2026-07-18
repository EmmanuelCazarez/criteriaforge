package io.github.emmanuelcazarez.criteriaforge.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class OrderQueryApiTest {

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void exposesDynamicFiltersNestedProjectionsSortAndPagination() throws Exception {
        mockMvc.perform(get("/api/orders")
                .queryParam("status_eq", "PAID")
                .queryParam("total_gte", "100")
                .queryParam("fields", "id,customer.name,total")
                .queryParam("sort", "-total")
                .queryParam("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(20))
            .andExpect(jsonPath("$.content[0].total").value(250.00))
            .andExpect(jsonPath("$.content[0].customer.name").value("Luis"))
            .andExpect(jsonPath("$.content[1].customer.name").value("Ana"));
    }

    @Test
    void rejectsHiddenFieldsWithAStableHttpError() throws Exception {
        mockMvc.perform(get("/api/orders").queryParam("internalNote_eq", "private"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"))
            .andExpect(jsonPath("$.path").value("internalNote"));
    }

    @Test
    void enablesRelationshipQueriesThroughAnExplicitExamplePolicy() throws Exception {
        mockMvc.perform(get("/api/orders")
                .queryParam("customer.country_eq", "MX")
                .queryParam("fields", "reference,customer.name")
                .queryParam("sort", "reference")
                .queryParam("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.content[0].reference").value("ORD-100"))
            .andExpect(jsonPath("$.content[1].reference").value("ORD-101"));
    }
}
