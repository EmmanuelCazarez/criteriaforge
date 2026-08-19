package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.Operator;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(JpaTestApplication.class)
@Transactional
class JpaQueryPolicyValidatorTest {

    @Autowired
    private EntityManager entityManager;

    private org.hibernate.stat.Statistics statistics;

    @BeforeEach
    void clearStatistics() {
        statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
    }

    @Test
    void rejectsQueryHiddenFieldsBeforeExecutingSql() {
        var query = QueryRequest.builder()
            .where(Filters.field("secretNote").eq("value"))
            .build();

        assertRejected(query, QueryPolicy.defaults(), QueryErrorCode.FIELD_NOT_ALLOWED);
    }

    @Test
    void explicitDenialsWinOverAllowlists() {
        var policy = QueryPolicy.builder()
            .allowFields("reference")
            .denyFields("reference")
            .build();

        assertRejected(
            QueryRequest.builder().where(Filters.field("reference").eq("A")).build(),
            policy,
            QueryErrorCode.FIELD_NOT_ALLOWED);
    }

    @Test
    void relationshipTraversalIsDisabledByDefault() {
        assertRejected(
            QueryRequest.builder().where(Filters.field("customer.name").eq("Ana")).build(),
            QueryPolicy.defaults(),
            QueryErrorCode.RELATIONSHIP_TRAVERSAL_DISABLED);
    }

    @Test
    void relationshipDepthAndPerFieldOperatorsAreEnforced() {
        var shallow = QueryPolicy.builder()
            .relationshipTraversal(true)
            .maxDepth(0)
            .build();
        assertRejected(
            QueryRequest.builder().where(Filters.field("customer.name").eq("Ana")).build(),
            shallow,
            QueryErrorCode.RELATIONSHIP_DEPTH_EXCEEDED);

        var equalityOnly = QueryPolicy.builder()
            .allowOperators("total", Operator.EQ)
            .build();
        assertRejected(
            QueryRequest.builder().where(Filters.field("total").gt("10")).build(),
            equalityOnly,
            QueryErrorCode.UNSUPPORTED_OPERATOR);
    }

    @Test
    void toManyProjectionIsRejectedDuringPreflight() {
        assertRejected(
            QueryRequest.builder().select("items.product.name").build(),
            QueryPolicy.builder().relationshipTraversal(true).build(),
            QueryErrorCode.UNSUPPORTED_PROJECTION);
    }

    @Test
    void validatesEachOperationWithItsOwnPermission() {
        var policy = QueryPolicy.builder()
            .allowProjectionFields("reference")
            .allowFilterFields("status")
            .allowSortFields("createdAt")
            .build();
        var query = QueryRequest.builder()
            .select("reference")
            .where(Filters.field("status").eq("PAID"))
            .orderByAscending("createdAt")
            .limit(10)
            .build();
        var executor = new JpaQueryEngine(entityManager, ignored -> policy);

        assertThatCode(() -> executor.findProjected(OrderEntity.class, query))
            .doesNotThrowAnyException();
    }

    @Test
    void projectionPermissionDoesNotGrantFilterOrSortPermission() {
        var policy = QueryPolicy.builder()
            .allowProjectionFields("reference")
            .allowFilterFields("status")
            .allowSortFields("createdAt")
            .build();

        assertRejected(
            QueryRequest.builder().where(Filters.field("reference").eq("A")).build(),
            policy,
            QueryErrorCode.FIELD_NOT_ALLOWED);
        assertRejected(
            QueryRequest.builder().orderByAscending("reference").build(),
            policy,
            QueryErrorCode.FIELD_NOT_ALLOWED);
    }

    @Test
    void filterPermissionDoesNotGrantProjectionPermission() {
        var policy = QueryPolicy.builder()
            .allowProjectionFields("reference")
            .allowFilterFields("status")
            .build();

        assertRejected(
            QueryRequest.builder().select("status").build(),
            policy,
            QueryErrorCode.FIELD_NOT_ALLOWED);
    }

    @Test
    void rejectsAToManyConfiguredTieBreakerBeforeExecutingSql() {
        var policy = QueryPolicy.builder()
            .relationshipTraversal(true)
            .tieBreaker("items.product.name")
            .build();

        assertRejected(
            QueryRequest.builder().limit(10).build(),
            policy,
            QueryErrorCode.UNSUPPORTED_PROJECTION);
    }

    @Test
    void trustsAConfiguredTieBreakerWithoutGrantingCallerAccess() {
        var policy = QueryPolicy.builder()
            .allowSortFields("reference")
            .denyFields("secretNote")
            .tieBreaker("secretNote")
            .build();
        var executor = new JpaQueryEngine(entityManager, ignored -> policy);

        assertThatCode(() -> executor.findAll(
            OrderEntity.class,
            QueryRequest.builder().orderByAscending("reference").limit(10).build()))
            .doesNotThrowAnyException();
    }

    private void assertRejected(
            QueryRequest query, QueryPolicy policy, QueryErrorCode expectedCode) {
        var executor = new JpaQueryEngine(entityManager, ignored -> policy);

        assertThatThrownBy(() -> {
            if (query.fields().isEmpty()) {
                executor.findAll(OrderEntity.class, query);
            } else {
                executor.findProjected(OrderEntity.class, query);
            }
        }).isInstanceOfSatisfying(QueryValidationException.class, error ->
            assertThat(error.code()).isEqualTo(expectedCode));
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }
}
