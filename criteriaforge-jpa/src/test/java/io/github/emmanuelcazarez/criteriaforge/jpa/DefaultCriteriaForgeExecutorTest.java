package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.PageSpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.SortSpec;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.CustomerEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderItemEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderStatus;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.ProductEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(JpaTestApplication.class)
@Transactional
class DefaultCriteriaForgeExecutorTest {

    @Autowired
    private EntityManager entityManager;

    private CriteriaForgeExecutor executor;

    @BeforeEach
    void setUp() {
        var ana = new CustomerEntity("Ana", "MX");
        var bob = new CustomerEntity("Bob", "US");
        entityManager.persist(ana);
        entityManager.persist(bob);
        entityManager.persist(order("FIRST", "20.00", ana));
        entityManager.persist(order("SECOND", "40.00", ana));
        entityManager.persist(order("THIRD", "60.00", bob));
        entityManager.persist(order("FOURTH", "80.00", bob));
        entityManager.flush();
        entityManager.clear();

        var policy = QueryPolicy.builder()
            .maxPageSize(10)
            .relationshipTraversal(true)
            .build();
        executor = new DefaultCriteriaForgeExecutor(entityManager, ignored -> policy);
    }

    @Test
    void appliesDeclaredSortsAndOffsetPaginationWhileCountingAllMatches() {
        var query = QuerySpec.builder()
            .sort(SortSpec.desc("total"), SortSpec.asc("reference"))
            .page(PageSpec.offset(1, 2))
            .build();

        var result = executor.findAll(OrderEntity.class, query);

        assertThat(result.content()).extracting(OrderEntity::getReference)
            .containsExactly("THIRD", "SECOND");
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.offset()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(2);
    }

    @Test
    void defaultsToTheActualJpaIdentifierEvenWhenItIsNotNamedId() {
        var result = executor.findAll(
            OrderEntity.class,
            QuerySpec.builder().page(PageSpec.offset(0, 10)).build());

        assertThat(result.content()).extracting(OrderEntity::getReference)
            .containsExactly("FIRST", "SECOND", "THIRD", "FOURTH");
    }

    @Test
    void returnsDistinctRootsAndCountsForPluralRelationshipFilters() {
        var widget = new ProductEntity("Widget");
        entityManager.persist(widget);
        var firstItem = new OrderItemEntity(widget);
        var secondItem = new OrderItemEntity(widget);
        entityManager.persist(firstItem);
        entityManager.persist(secondItem);
        var customer = new CustomerEntity("Carla", "MX");
        entityManager.persist(customer);
        var duplicatedByJoin = order("WITH-TWO-WIDGETS", "100.00", customer);
        duplicatedByJoin.addItem(firstItem);
        duplicatedByJoin.addItem(secondItem);
        entityManager.persist(duplicatedByJoin);
        entityManager.flush();
        entityManager.clear();

        var query = QuerySpec.builder()
            .where(Filters.eq("items.product.name", "Widget"))
            .page(PageSpec.offset(0, 10))
            .build();

        var result = executor.findAll(OrderEntity.class, query);

        assertThat(result.content()).extracting(OrderEntity::getReference)
            .containsExactly("WITH-TWO-WIDGETS");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void rejectsProjectionFieldsUntilTheProjectionApiIsUsed() {
        var query = QuerySpec.builder().select("reference").build();

        assertThatThrownBy(() -> executor.findAll(OrderEntity.class, query))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.UNSUPPORTED_PROJECTION));
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
