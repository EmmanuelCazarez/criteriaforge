package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.PageSpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryComplexityValidator;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/** Default Criteria API implementation of {@link CriteriaForgeExecutor}. */
public final class DefaultCriteriaForgeExecutor implements CriteriaForgeExecutor {
    private final EntityManager entityManager;
    private final QueryPolicyResolver policyResolver;
    private final QueryComplexityValidator complexityValidator = new QueryComplexityValidator();
    private final JpaPredicateBuilder predicateBuilder;
    private final JpaSortBuilder sortBuilder;
    private final JpaSelectionBuilder selectionBuilder;
    private final JpaQueryPolicyValidator policyValidator;
    private final NestedMapAssembler mapAssembler = new NestedMapAssembler();

    public DefaultCriteriaForgeExecutor(
            EntityManager entityManager, QueryPolicyResolver policyResolver) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.policyResolver = Objects.requireNonNull(
            policyResolver, "policyResolver must not be null");
        var pathResolver = new JpaPathResolver(entityManager.getMetamodel());
        predicateBuilder = new JpaPredicateBuilder(pathResolver, new JpaValueConverter());
        sortBuilder = new JpaSortBuilder(pathResolver);
        selectionBuilder = new JpaSelectionBuilder(pathResolver, entityManager.getMetamodel());
        policyValidator = new JpaQueryPolicyValidator(entityManager.getMetamodel());
    }

    @Override
    public <T> QueryResult<T> findAll(Class<T> entityType, QuerySpec query) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (!query.fields().isEmpty()) {
            throw new QueryValidationException(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Use findProjected when selecting fields");
        }

        var policy = Objects.requireNonNull(
            policyResolver.resolve(entityType), "resolved query policy must not be null");
        complexityValidator.validate(query, policy);
        policyValidator.validate(entityType, query, policy);
        var page = query.page().orElse(PageSpec.offset(0, policy.maxPageSize()));

        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var contentQuery = criteriaBuilder.createQuery(entityType);
        var contentRoot = contentQuery.from(entityType);
        var contentJoins = new JoinRegistry(contentRoot);
        contentQuery.select(contentRoot);
        query.filter().ifPresent(expression -> contentQuery.where(predicateBuilder.build(
            expression, contentRoot, criteriaBuilder, policy, contentJoins)));

        if (query.sorts().isEmpty()) {
            contentQuery.orderBy(criteriaBuilder.asc(contentRoot.get(identifierName(entityType))));
        } else {
            contentQuery.orderBy(sortBuilder.build(
                query.sorts(), contentRoot, criteriaBuilder, policy, contentJoins));
        }
        contentQuery.distinct(hasPluralJoin(contentRoot));

        var content = entityManager.createQuery(contentQuery)
            .setFirstResult(page.offset())
            .setMaxResults(page.limit())
            .getResultList();
        var total = count(entityType, query, policy);
        return new QueryResult<>(content, total, page.offset(), page.limit());
    }

    @Override
    public QueryResult<Map<String, Object>> findProjected(
            Class<?> entityType, QuerySpec query) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (query.fields().isEmpty()) {
            throw new QueryValidationException(
                QueryErrorCode.MALFORMED_QUERY,
                "At least one projection field is required");
        }

        var policy = Objects.requireNonNull(
            policyResolver.resolve(entityType), "resolved query policy must not be null");
        complexityValidator.validate(query, policy);
        policyValidator.validate(entityType, query, policy);
        var page = query.page().orElse(PageSpec.offset(0, policy.maxPageSize()));

        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var contentQuery = criteriaBuilder.createTupleQuery();
        var contentRoot = contentQuery.from(entityType);
        var contentJoins = new JoinRegistry(contentRoot);
        contentQuery.multiselect(selectionBuilder.build(
            query.fields(), contentRoot, policy, contentJoins));
        query.filter().ifPresent(expression -> contentQuery.where(predicateBuilder.build(
            expression, contentRoot, criteriaBuilder, policy, contentJoins)));
        if (query.sorts().isEmpty()) {
            contentQuery.orderBy(criteriaBuilder.asc(contentRoot.get(identifierName(entityType))));
        } else {
            contentQuery.orderBy(sortBuilder.build(
                query.sorts(), contentRoot, criteriaBuilder, policy, contentJoins));
        }
        contentQuery.distinct(hasPluralJoin(contentRoot));

        var content = entityManager.createQuery(contentQuery)
            .setFirstResult(page.offset())
            .setMaxResults(page.limit())
            .getResultList().stream()
            .map(tuple -> mapAssembler.assemble(query.fields(), tupleValues(tuple)))
            .toList();
        var total = count(entityType, query, policy);
        return new QueryResult<>(content, total, page.offset(), page.limit());
    }

    private static java.util.List<?> tupleValues(Tuple tuple) {
        return IntStream.range(0, tuple.getElements().size())
            .mapToObj(tuple::get)
            .toList();
    }

    private <T> long count(Class<T> entityType, QuerySpec query, QueryPolicy policy) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var countQuery = criteriaBuilder.createQuery(Long.class);
        var countRoot = countQuery.from(entityType);
        var countJoins = new JoinRegistry(countRoot);
        query.filter().ifPresent(expression -> countQuery.where(predicateBuilder.build(
            expression, countRoot, criteriaBuilder, policy, countJoins)));
        countQuery.select(hasPluralJoin(countRoot)
            ? criteriaBuilder.countDistinct(countRoot)
            : criteriaBuilder.count(countRoot));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private String identifierName(Class<?> entityType) {
        try {
            var entity = entityManager.getMetamodel().entity(entityType);
            var idType = entity.getIdType();
            return entity.getId(idType.getJavaType()).getName();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new QueryValidationException(
                QueryErrorCode.MALFORMED_QUERY,
                "Entity must expose one JPA identifier",
                null,
                exception);
        }
    }

    private static boolean hasPluralJoin(From<?, ?> from) {
        for (Join<?, ?> join : from.getJoins()) {
            if (join.getAttribute() instanceof PluralAttribute || hasPluralJoin(join)) {
                return true;
            }
        }
        return false;
    }
}
