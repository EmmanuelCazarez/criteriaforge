package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.PageSpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.SortSpec;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.CustomerEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(
    showSql = false,
    properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@ContextConfiguration(classes = JpaTestApplication.class)
class JpaProjectionTest {

    @Autowired
    private EntityManager entityManager;

    private CriteriaForgeExecutor executor;

    @BeforeEach
    void setUp() {
        var ana = new CustomerEntity("Ana", "MX");
        entityManager.persist(ana);
        entityManager.persist(order("LOW", "20.00", ana));
        entityManager.persist(order("HIGH", "120.00", ana));
        entityManager.persist(order("NO-CUSTOMER", "50.00", null));
        entityManager.flush();
        entityManager.clear();

        var policy = QueryPolicy.builder()
            .relationshipTraversal(true)
            .build();
        executor = new DefaultCriteriaForgeExecutor(entityManager, ignored -> policy);
    }

    @Test
    void selectsOrderedRootAndNestedFieldsAndSortsByAnUnselectedField() {
        var query = QuerySpec.builder()
            .select("reference", "customer.name", "customer.country")
            .sort(SortSpec.desc("total"))
            .page(PageSpec.offset(0, 2))
            .build();

        var result = executor.findProjected(OrderEntity.class, query);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0))
            .containsEntry("reference", "HIGH")
            .doesNotContainKey("total");
        assertThat((Map<String, Object>) result.content().get(0).get("customer"))
            .containsEntry("name", "Ana")
            .containsEntry("country", "MX");
        assertThat(result.content().get(1)).containsEntry("reference", "NO-CUSTOMER");
        assertThat((Map<String, Object>) result.content().get(1).get("customer"))
            .containsEntry("name", null)
            .containsEntry("country", null);
    }

    @Test
    void rejectsProjectionThroughAToManyRelationship() {
        var query = QuerySpec.builder().select("items.product.name").build();

        assertThatThrownBy(() -> executor.findProjected(OrderEntity.class, query))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.UNSUPPORTED_PROJECTION);
                assertThat(error.path()).contains("items.product.name");
            });
    }

    @Test
    void requiresAtLeastOneProjectionField() {
        assertThatThrownBy(() -> executor.findProjected(
            OrderEntity.class,
            QuerySpec.builder().build()))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.MALFORMED_QUERY));
    }

    private static OrderEntity order(
            String reference, String total, CustomerEntity customer) {
        return new OrderEntity(
            reference,
            OrderStatus.PAID,
            new BigDecimal(total),
            customer);
    }
}
