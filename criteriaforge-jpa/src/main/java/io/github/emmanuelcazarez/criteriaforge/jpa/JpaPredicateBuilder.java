package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.Condition;
import io.github.emmanuelcazarez.criteriaforge.core.FilterExpression;
import io.github.emmanuelcazarez.criteriaforge.core.FilterGroup;
import io.github.emmanuelcazarez.criteriaforge.core.Negation;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builds typed Criteria predicates from the core filter expression tree. */
public final class JpaPredicateBuilder {
    private final JpaPathResolver pathResolver;
    private final JpaValueConverter valueConverter;
    private final OperatorCompatibility compatibility = new OperatorCompatibility();

    public JpaPredicateBuilder(
            JpaPathResolver pathResolver, JpaValueConverter valueConverter) {
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.valueConverter = Objects.requireNonNull(
            valueConverter, "valueConverter must not be null");
    }

    public Predicate build(
            FilterExpression expression,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy) {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(criteriaBuilder, "criteriaBuilder must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        return build(expression, root, criteriaBuilder, policy, new JoinRegistry(root));
    }

    Predicate build(
            FilterExpression expression,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy,
            JoinRegistry joins) {
        Objects.requireNonNull(joins, "joins must not be null");
        return buildNode(expression, root, criteriaBuilder, policy, joins);
    }

    private Predicate buildNode(
            FilterExpression expression,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy,
            JoinRegistry joins) {
        if (expression instanceof Condition condition) {
            return buildCondition(condition, root, criteriaBuilder, policy, joins);
        }
        if (expression instanceof Negation negation) {
            return criteriaBuilder.not(
                buildNode(negation.expression(), root, criteriaBuilder, policy, joins));
        }
        var group = (FilterGroup) expression;
        var predicates = group.children().stream()
            .map(child -> buildNode(child, root, criteriaBuilder, policy, joins))
            .toArray(Predicate[]::new);
        return group.junction() == FilterGroup.Junction.AND
            ? criteriaBuilder.and(predicates)
            : criteriaBuilder.or(predicates);
    }

    private Predicate buildCondition(
            Condition condition,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy,
            JoinRegistry joins) {
        var resolved = pathResolver.resolve(root, condition.field(), joins);
        validatePolicy(condition, resolved, policy);
        compatibility.validate(condition.operator(), resolved.javaType(), condition.field());
        var typedValues = valueConverter.convertAll(condition.values(), resolved.javaType());
        var path = resolved.path();

        return switch (condition.operator()) {
            case EQ -> criteriaBuilder.equal(path, typedValues.get(0));
            case NE -> criteriaBuilder.notEqual(path, typedValues.get(0));
            case GT -> greaterThan(criteriaBuilder, path, typedValues.get(0));
            case GTE -> greaterThanOrEqual(criteriaBuilder, path, typedValues.get(0));
            case LT -> lessThan(criteriaBuilder, path, typedValues.get(0));
            case LTE -> lessThanOrEqual(criteriaBuilder, path, typedValues.get(0));
            case LIKE -> criteriaBuilder.like(
                criteriaBuilder.lower(path.as(String.class)),
                ((String) typedValues.get(0)).toLowerCase(Locale.ROOT));
            case IN -> in(criteriaBuilder, path, typedValues);
            case BETWEEN -> between(criteriaBuilder, path, typedValues.get(0), typedValues.get(1));
            case IS_NULL -> criteriaBuilder.isNull(path);
            case IS_NOT_NULL -> criteriaBuilder.isNotNull(path);
        };
    }

    private static void validatePolicy(
            Condition condition, JpaResolvedPath resolved, QueryPolicy policy) {
        if (!policy.isFieldAllowed(condition.field())) {
            throw new QueryValidationException(
                QueryErrorCode.FIELD_NOT_ALLOWED,
                "Field is not allowed by the query policy",
                condition.field());
        }
        if (!policy.isOperatorAllowed(condition.field(), condition.operator())) {
            throw new QueryValidationException(
                QueryErrorCode.UNSUPPORTED_OPERATOR,
                "Operator is not allowed for this field",
                condition.field());
        }
        if (resolved.relationshipDepth() > 0 && !policy.relationshipTraversal()) {
            throw new QueryValidationException(
                QueryErrorCode.RELATIONSHIP_TRAVERSAL_DISABLED,
                "Relationship traversal is disabled",
                condition.field());
        }
        if (resolved.relationshipDepth() > policy.maxDepth()) {
            throw new QueryValidationException(
                QueryErrorCode.RELATIONSHIP_DEPTH_EXCEEDED,
                "Relationship path exceeds maximum depth " + policy.maxDepth(),
                condition.field());
        }
    }

    private static Predicate in(
            CriteriaBuilder criteriaBuilder, Path<?> path, List<?> typedValues) {
        CriteriaBuilder.In<Object> predicate = criteriaBuilder.in(path);
        typedValues.forEach(predicate::value);
        return predicate;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate greaterThan(
            CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.greaterThan((Expression<? extends Comparable>) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate greaterThanOrEqual(
            CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.greaterThanOrEqualTo(
            (Expression<? extends Comparable>) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate lessThan(
            CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.lessThan((Expression<? extends Comparable>) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate lessThanOrEqual(
            CriteriaBuilder builder, Path<?> path, Object value) {
        return builder.lessThanOrEqualTo(
            (Expression<? extends Comparable>) path, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate between(
            CriteriaBuilder builder, Path<?> path, Object lower, Object upper) {
        return builder.between(
            (Expression<? extends Comparable>) path,
            (Comparable) lower,
            (Comparable) upper);
    }
}
