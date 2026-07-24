package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.FilterExpression;
import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.CustomerEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderEntity;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(JpaTestApplication.class)
@Transactional
class JpaPredicateBuilderTest {

    @Autowired
    private EntityManager entityManager;

    private JpaPredicateBuilder predicateBuilder;

    @BeforeEach
    void setUp() {
        var ana = new CustomerEntity("Ana", "MX");
        var bob = new CustomerEntity("Bob", "US");
        entityManager.persist(ana);
        entityManager.persist(bob);
        entityManager.persist(order("PAID-HIGH", OrderStatus.PAID, "150.00", ana));
        entityManager.persist(order("PAID-LOW", OrderStatus.PAID, "5.00", ana));
        entityManager.persist(order("CREATED-MID", OrderStatus.CREATED, "50.00", bob));
        entityManager.persist(order("CANCELLED-NULL", OrderStatus.CANCELLED, null, bob));
        entityManager.flush();
        entityManager.clear();

        predicateBuilder = new JpaPredicateBuilder(
            new JpaPathResolver(entityManager.getMetamodel()),
            new JpaValueConverter());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operatorCases")
    void executesEverySupportedOperator(
            String description, FilterExpression expression, List<String> expected) {
        assertThat(execute(expression)).containsExactlyElementsOf(expected);
    }

    @Test
    void preservesNestedBooleanPrecedence() {
        var expression = Filters.field("status").eq(OrderStatus.PAID)
            .and(Filters.field("total").lt(new BigDecimal("10"))
                .or(Filters.field("total").gte(new BigDecimal("100"))));

        assertThat(execute(expression)).containsExactly("PAID-HIGH", "PAID-LOW");
    }

    @Test
    void supportsRelationshipPredicatesWhenPolicyAllowsTraversal() {
        assertThat(execute(Filters.field("customer.country").eq("MX")))
            .containsExactly("PAID-HIGH", "PAID-LOW");
    }

    @Test
    void rejectsOperatorsThatDoNotMatchTheResolvedType() {
        assertThatThrownBy(() -> execute(Filters.field("total").like("15%")))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.INCOMPATIBLE_OPERATOR);
                assertThat(error.path()).contains("total");
            });
    }

    private List<String> execute(FilterExpression expression) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var query = criteriaBuilder.createQuery(OrderEntity.class);
        var root = query.from(OrderEntity.class);
        query.select(root)
            .where(predicateBuilder.build(
                expression,
                root,
                criteriaBuilder,
                QueryPolicy.builder().relationshipTraversal(true).build()))
            .orderBy(criteriaBuilder.asc(root.get("reference")));
        return entityManager.createQuery(query).getResultList().stream()
            .map(OrderEntity::getReference)
            .toList();
    }

    private static Stream<Arguments> operatorCases() {
        return Stream.of(
            Arguments.of("EQ", Filters.field("status").eq(OrderStatus.PAID),
                List.of("PAID-HIGH", "PAID-LOW")),
            Arguments.of("NE", Filters.field("status").ne(OrderStatus.PAID),
                List.of("CANCELLED-NULL", "CREATED-MID")),
            Arguments.of("GT", Filters.field("total").gt(new BigDecimal("50")),
                List.of("PAID-HIGH")),
            Arguments.of("GTE", Filters.field("total").gte(new BigDecimal("50")),
                List.of("CREATED-MID", "PAID-HIGH")),
            Arguments.of("LT", Filters.field("total").lt(new BigDecimal("50")),
                List.of("PAID-LOW")),
            Arguments.of("LTE", Filters.field("total").lte(new BigDecimal("50")),
                List.of("CREATED-MID", "PAID-LOW")),
            Arguments.of("LIKE", Filters.field("reference").like("paid-%"),
                List.of("PAID-HIGH", "PAID-LOW")),
            Arguments.of("IN", Filters.field("status").in(OrderStatus.CREATED, OrderStatus.CANCELLED),
                List.of("CANCELLED-NULL", "CREATED-MID")),
            Arguments.of("BETWEEN", Filters.field("total")
                .between(new BigDecimal("5"), new BigDecimal("50")),
                List.of("CREATED-MID", "PAID-LOW")),
            Arguments.of("IS_NULL", Filters.field("total").isNull(),
                List.of("CANCELLED-NULL")),
            Arguments.of("IS_NOT_NULL", Filters.field("total").isNotNull(),
                List.of("CREATED-MID", "PAID-HIGH", "PAID-LOW")),
            Arguments.of("NOT", Filters.field("status").eq(OrderStatus.PAID).not(),
                List.of("CANCELLED-NULL", "CREATED-MID")));
    }

    private static OrderEntity order(
            String reference, OrderStatus status, String total, CustomerEntity customer) {
        return new OrderEntity(
            reference,
            status,
            total == null ? null : new BigDecimal(total),
            customer);
    }
}
