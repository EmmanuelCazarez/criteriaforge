package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.Pagination;
import io.github.emmanuelcazarez.criteriaforge.core.QueryComplexityValidator;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Default Criteria API implementation of {@link QueryEngine}. */
public final class JpaQueryEngine implements QueryEngine {
    private final EntityManager entityManager;
    private final QueryPolicyProvider policyProvider;
    private final QueryComplexityValidator complexityValidator = new QueryComplexityValidator();
    private final JpaPredicateBuilder predicateBuilder;
    private final JpaSortBuilder sortBuilder;
    private final JpaSelectionBuilder selectionBuilder;
    private final JpaQueryPolicyValidator policyValidator;
    private final NestedMapAssembler mapAssembler = new NestedMapAssembler();

    public JpaQueryEngine(
            EntityManager entityManager, QueryPolicyProvider policyProvider) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.policyProvider = Objects.requireNonNull(
            policyProvider, "policyProvider must not be null");
        var pathResolver = new JpaPathResolver(entityManager.getMetamodel());
        predicateBuilder = new JpaPredicateBuilder(pathResolver, new JpaValueConverter());
        sortBuilder = new JpaSortBuilder(pathResolver);
        selectionBuilder = new JpaSelectionBuilder(pathResolver, entityManager.getMetamodel());
        policyValidator = new JpaQueryPolicyValidator(entityManager.getMetamodel());
    }

    @Override
    public QueryResult<?> execute(Class<?> entityType, QueryRequest query) {
        Objects.requireNonNull(query, "query must not be null");
        return query.fields().isEmpty()
            ? findAll(entityType, query)
            : findProjected(entityType, query);
    }

    <T> QueryResult<T> findAll(Class<T> entityType, QueryRequest query) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (!query.fields().isEmpty()) {
            throw new QueryValidationException(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Use findProjected when selecting fields");
        }

        var policy = Objects.requireNonNull(
            policyProvider.policyFor(entityType), "provided query policy must not be null");
        complexityValidator.validate(query, policy);
        policyValidator.validate(entityType, query, policy);
        var pagination = query.pagination().orElse(new Pagination(0, policy.maxPageSize()));
        var sortOrders = query.sorting().map(sorting -> sorting.orders()).orElseGet(List::of);

        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var contentQuery = criteriaBuilder.createQuery(entityType);
        var contentRoot = contentQuery.from(entityType);
        var contentJoins = new JoinRegistry(contentRoot);
        contentQuery.select(contentRoot);
        query.filter().ifPresent(expression -> contentQuery.where(predicateBuilder.build(
            expression, contentRoot, criteriaBuilder, policy, contentJoins)));

        if (sortOrders.isEmpty()) {
            contentQuery.orderBy(criteriaBuilder.asc(contentRoot.get(identifierName(entityType))));
        } else {
            contentQuery.orderBy(sortBuilder.build(
                sortOrders, contentRoot, criteriaBuilder, policy, contentJoins));
        }
        contentQuery.distinct(hasPluralJoin(contentRoot));

        var content = entityManager.createQuery(contentQuery)
            .setFirstResult(pagination.offset())
            .setMaxResults(pagination.limit())
            .getResultList();
        var total = count(entityType, query, policy);
        return new QueryResult<>(
            content, total, pagination.offset(), pagination.limit());
    }

    QueryResult<Map<String, Object>> findProjected(
            Class<?> entityType, QueryRequest query) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (query.fields().isEmpty()) {
            throw new QueryValidationException(
                QueryErrorCode.MALFORMED_QUERY,
                "At least one projection field is required");
        }

        var policy = Objects.requireNonNull(
            policyProvider.policyFor(entityType), "provided query policy must not be null");
        complexityValidator.validate(query, policy);
        policyValidator.validate(entityType, query, policy);
        var pagination = query.pagination().orElse(new Pagination(0, policy.maxPageSize()));
        var sortOrders = query.sorting().map(sorting -> sorting.orders()).orElseGet(List::of);

        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var contentQuery = criteriaBuilder.createTupleQuery();
        var contentRoot = contentQuery.from(entityType);
        var contentJoins = new JoinRegistry(contentRoot);
        var selections = new ArrayList<Selection<?>>(selectionBuilder.build(
            query.fields(), contentRoot, policy, contentJoins));
        query.filter().ifPresent(expression -> contentQuery.where(predicateBuilder.build(
            expression, contentRoot, criteriaBuilder, policy, contentJoins)));
        List<Order> orders = sortOrders.isEmpty()
            ? List.of(criteriaBuilder.asc(contentRoot.get(identifierName(entityType))))
            : sortBuilder.build(
                sortOrders, contentRoot, criteriaBuilder, policy, contentJoins);
        var distinct = hasPluralJoin(contentRoot);
        if (distinct) {
            var selectedSources = query.fields().stream()
                .map(field -> policy.resolveField(field.source()))
                .collect(Collectors.toUnmodifiableSet());
            var sortSources = sortOrders.isEmpty()
                ? List.of(identifierName(entityType))
                : sortOrders.stream()
                    .map(sort -> policy.resolveField(sort.field()))
                    .toList();
            addHiddenSortSelections(
                selections, orders, sortSources, selectedSources);
        }
        contentQuery.multiselect(selections);
        contentQuery.orderBy(orders);
        contentQuery.distinct(distinct);

        var content = entityManager.createQuery(contentQuery)
            .setFirstResult(pagination.offset())
            .setMaxResults(pagination.limit())
            .getResultList().stream()
            .map(tuple -> mapAssembler.assemble(
                query.fields(), tupleValues(tuple, query.fields().size())))
            .toList();
        var total = count(entityType, query, policy);
        return new QueryResult<>(
            content, total, pagination.offset(), pagination.limit());
    }

    private static java.util.List<?> tupleValues(Tuple tuple, int visibleSelections) {
        return IntStream.range(0, visibleSelections)
            .mapToObj(tuple::get)
            .toList();
    }

    private static void addHiddenSortSelections(
            List<Selection<?>> selections,
            List<Order> orders,
            List<String> sortSources,
            Set<String> selectedSources) {
        for (int index = 0; index < orders.size(); index++) {
            if (!selectedSources.contains(sortSources.get(index))) {
                selections.add(orders.get(index).getExpression());
            }
        }
    }

    private <T> long count(Class<T> entityType, QueryRequest query, QueryPolicy policy) {
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
