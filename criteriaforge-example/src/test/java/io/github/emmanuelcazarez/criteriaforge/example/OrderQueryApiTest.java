package io.github.emmanuelcazarez.criteriaforge.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderQueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesDynamicFiltersNestedProjectionsSortAndPagination() throws Exception {
        mockMvc.perform(get("/api/orders")
                .param("status_eq", "PAID")
                .param("total_gte", "100")
                .param("fields", "id,customer.name,total")
                .param("sort", "-total")
                .param("limit", "20"))
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
        mockMvc.perform(get("/api/orders").param("internalNote_eq", "private"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FIELD_NOT_ALLOWED"))
            .andExpect(jsonPath("$.path").value("internalNote"));
    }

    @Test
    void enablesRelationshipQueriesThroughAnExplicitExamplePolicy() throws Exception {
        mockMvc.perform(get("/api/orders")
                .param("customer.country_eq", "MX")
                .param("fields", "reference,customer.name")
                .param("sort", "reference")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.content[0].reference").value("ORD-100"))
            .andExpect(jsonPath("$.content[1].reference").value("ORD-101"));
    }
}
