package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.CustomerEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderItemEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderStatus;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.ProductEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(JpaTestApplication.class)
@Transactional
@ActiveProfiles("postgresql")
class PostgreSqlQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private EntityManager entityManager;

    private JpaQueryEngine executor;

    @BeforeEach
    void setUp() {
        var customer = new CustomerEntity("Postgres Customer", "MX");
        entityManager.persist(customer);

        var alpha = order(
            "Alpha-PAID",
            "1234567890.1234",
            customer,
            OffsetDateTime.parse("2026-07-18T10:15:30-07:00"));
        var beta = order(
            "beta-pending",
            "0.0001",
            customer,
            OffsetDateTime.parse("2026-07-19T10:15:30-07:00"));
        entityManager.persist(alpha);
        entityManager.persist(beta);

        var widget = new ProductEntity("Widget");
        entityManager.persist(widget);
        var firstItem = new OrderItemEntity(widget);
        var secondItem = new OrderItemEntity(widget);
        entityManager.persist(firstItem);
        entityManager.persist(secondItem);
        alpha.addItem(firstItem);
        alpha.addItem(secondItem);

        entityManager.flush();
        entityManager.clear();

        var policy = QueryPolicy.builder()
            .maxPageSize(10)
            .relationshipTraversal(true)
            .build();
        executor = new JpaQueryEngine(entityManager, ignored -> policy);
    }

    @Test
    void preservesPostgreSqlTextDecimalEnumAndOffsetDateTimeSemantics() {
        var query = QueryRequest.builder()
            .where(Filters.field("reference").like("alpha-%")
                .and(Filters.field("status").eq("paid"))
                .and(Filters.field("total").eq("1234567890.1234"))
                .and(Filters.field("createdAt").gte("2026-07-18T17:15:30Z")))
            .limit(10)
            .build();

        var result = executor.findAll(OrderEntity.class, query);

        assertThat(result.content()).singleElement().satisfies(order -> {
            assertThat(order.getReference()).isEqualTo("Alpha-PAID");
            assertThat(order.getTotal()).isEqualByComparingTo("1234567890.1234");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        });
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void keepsDistinctPaginationAndCountsAcrossPluralJoins() {
        var query = QueryRequest.builder()
            .where(Filters.field("items.product.name").eq("Widget"))
            .orderByAscending("reference")
            .limit(1)
            .build();

        var result = executor.findAll(OrderEntity.class, query);

        assertThat(result.content()).extracting(OrderEntity::getReference)
            .containsExactly("Alpha-PAID");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(1);
    }

    private static OrderEntity order(
            String reference,
            String total,
            CustomerEntity customer,
            OffsetDateTime createdAt) {
        var order = new OrderEntity(
            reference,
            reference.contains("PAID") ? OrderStatus.PAID : OrderStatus.CREATED,
            new BigDecimal(total),
            customer);
        order.setCreatedAt(createdAt);
        return order;
    }
}
