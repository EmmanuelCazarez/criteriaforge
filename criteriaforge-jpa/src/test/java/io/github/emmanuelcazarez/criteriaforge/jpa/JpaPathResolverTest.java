package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.jpa.model.OrderEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(
    showSql = false,
    properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@ContextConfiguration(classes = JpaTestApplication.class)
class JpaPathResolverTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void resolvesNestedPathsAndReusesTheirJoins() {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(OrderEntity.class);
        var root = query.from(OrderEntity.class);
        var joins = new JoinRegistry(root);
        var resolver = new JpaPathResolver(entityManager.getMetamodel());

        var first = resolver.resolve(root, "customer.name", joins);
        var second = resolver.resolve(root, "customer.country", joins);

        assertThat(first.javaType()).isEqualTo(String.class);
        assertThat(first.relationshipDepth()).isEqualTo(1);
        assertThat(first.plural()).isFalse();
        assertThat(second.relationshipDepth()).isEqualTo(1);
        assertThat(root.getJoins()).hasSize(1);
    }

    @Test
    void reportsPluralTraversalAndRelationshipDepth() {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(OrderEntity.class);
        var root = query.from(OrderEntity.class);

        var resolved = new JpaPathResolver(entityManager.getMetamodel())
            .resolve(root, "items.product.name", new JoinRegistry(root));

        assertThat(resolved.javaType()).isEqualTo(String.class);
        assertThat(resolved.relationshipDepth()).isEqualTo(2);
        assertThat(resolved.plural()).isTrue();
    }

    @Test
    void rejectsUnknownPersistentPathsWithAStableError() {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(OrderEntity.class);
        var root = query.from(OrderEntity.class);

        assertThatThrownBy(() -> new JpaPathResolver(entityManager.getMetamodel())
            .resolve(root, "customer.missing", new JoinRegistry(root)))
            .isInstanceOfSatisfying(QueryValidationException.class, error -> {
                assertThat(error.code()).isEqualTo(QueryErrorCode.UNKNOWN_FIELD);
                assertThat(error.path()).contains("customer.missing");
            });
    }
}
