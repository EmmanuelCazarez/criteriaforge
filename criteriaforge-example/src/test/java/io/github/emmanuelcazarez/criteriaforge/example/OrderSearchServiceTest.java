package io.github.emmanuelcazarez.criteriaforge.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderSearchServiceTest {

    @Autowired
    private OrderSearchService service;

    @Test
    void buildsTypedFiltersAndProjectionAliasesWithoutHttpInput() {
        var result = service.findPaidOrders(new BigDecimal("100"), List.of("MX", "US"));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
        @SuppressWarnings("unchecked")
        var first = (Map<String, Object>) result.content().get(0);
        assertThat(first.keySet()).containsExactly("reference", "buyer", "orderTotal");
        assertThat(first.get("reference")).isEqualTo("ORD-102");
        assertThat(first.get("buyer")).isEqualTo(Map.of("name", "Luis"));
        assertThat((BigDecimal) first.get("orderTotal")).isEqualByComparingTo("250.00");
    }
}
