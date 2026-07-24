package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.PageSpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
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
class JpaQueryEngineTest {

    @Autowired
    private EntityManager entityManager;

    private JpaQueryEngine executor;

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
        executor = new JpaQueryEngine(entityManager, ignored -> policy);
    }

    @Test
    void appliesDeclaredSortsAndOffsetPaginationWhileCountingAllMatches() {
        var query = QueryRequest.builder()
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
            QueryRequest.builder().page(PageSpec.offset(0, 10)).build());

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

        var query = QueryRequest.builder()
            .where(Filters.field("items.product.name").eq("Widget"))
            .page(PageSpec.offset(0, 10))
            .build();

        var result = executor.findAll(OrderEntity.class, query);

        assertThat(result.content()).extracting(OrderEntity::getReference)
            .containsExactly("WITH-TWO-WIDGETS");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void keepsSortSelectionsHiddenForDistinctPluralProjections() {
        var widget = new ProductEntity("Projected Widget");
        entityManager.persist(widget);
        var firstItem = new OrderItemEntity(widget);
        var secondItem = new OrderItemEntity(widget);
        entityManager.persist(firstItem);
        entityManager.persist(secondItem);
        var customer = new CustomerEntity("Diana", "MX");
        entityManager.persist(customer);
        var duplicatedByJoin = order("PROJECTED-WITH-TWO-ITEMS", "120.00", customer);
        duplicatedByJoin.addItem(firstItem);
        duplicatedByJoin.addItem(secondItem);
        entityManager.persist(duplicatedByJoin);
        entityManager.flush();
        entityManager.clear();

        var query = QueryRequest.builder()
            .select("reference", "total")
            .where(Filters.field("items.product.name").eq("Projected Widget"))
            .sort(SortSpec.desc("total"))
            .page(PageSpec.offset(0, 10))
            .build();

        var result = executor.findProjected(OrderEntity.class, query);

        assertThat(result.content()).singleElement().satisfies(row -> {
            assertThat(row.keySet()).containsExactly("reference", "total");
            assertThat(row.get("reference")).isEqualTo("PROJECTED-WITH-TWO-ITEMS");
            assertThat((BigDecimal) row.get("total")).isEqualByComparingTo("120.00");
        });
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void entityExecutionRejectsProjectionFields() {
        var query = QueryRequest.builder().select("reference").build();

        assertThatThrownBy(() -> executor.findAll(OrderEntity.class, query))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.UNSUPPORTED_PROJECTION));
    }

    @Test
    void projectsSelectedSourcesIntoPerRequestOutputPathsInRequestOrder() {
        var query = QueryRequest.builder()
            .selectAs("customer.name", "buyer.name")
            .selectAs("total", "orderTotal")
            .sort(SortSpec.asc("reference"))
            .page(PageSpec.offset(0, 1))
            .build();

        var result = executor.findProjected(OrderEntity.class, query);

        assertThat(result.content()).singleElement().satisfies(row -> {
            assertThat(row.keySet()).containsExactly("buyer", "orderTotal");
            assertThat(row.get("buyer")).isEqualTo(java.util.Map.of("name", "Ana"));
            assertThat((BigDecimal) row.get("orderTotal"))
                .isEqualByComparingTo(new BigDecimal("20.00"));
        });
    }

    @Test
    void resolvesStablePublicFieldNamesForFiltersSortsAndProjections() {
        var publicPolicy = QueryPolicy.builder()
            .allowFields("reference")
            .alias("amount", "total")
            .alias("buyerName", "customer.name")
            .relationshipTraversal(true)
            .build();
        var publicExecutor = new JpaQueryEngine(entityManager, ignored -> publicPolicy);
        var query = QueryRequest.builder()
            .selectAs("buyerName", "buyer.name")
            .selectAs("amount", "orderTotal")
            .where(Filters.field("amount").gte(new BigDecimal("40.00")))
            .sort(SortSpec.desc("amount"))
            .page(PageSpec.offset(0, 1))
            .build();

        var result = publicExecutor.findProjected(OrderEntity.class, query);

        assertThat(result.content()).singleElement().satisfies(row -> {
            assertThat(row.get("buyer")).isEqualTo(java.util.Map.of("name", "Bob"));
            assertThat((BigDecimal) row.get("orderTotal"))
                .isEqualByComparingTo("80.00");
        });
        assertThat(result.total()).isEqualTo(3);
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
