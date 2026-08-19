package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.SortDirection;
import io.github.emmanuelcazarez.criteriaforge.core.Sorting;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Objects;

/** Resolves declared dynamic sorts and enforces their field policies. */
final class JpaSortBuilder {
    private final JpaPathResolver pathResolver;

    JpaSortBuilder(JpaPathResolver pathResolver) {
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
    }

    List<Order> build(
            List<Sorting.Order> sorts,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy,
            JoinRegistry joins) {
        return sorts.stream()
            .map(sort -> build(sort, root, criteriaBuilder, policy, joins))
            .toList();
    }

    Order buildTrusted(
            String persistentPath,
            SortDirection direction,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy,
            JoinRegistry joins) {
        var resolved = pathResolver.resolve(root, persistentPath, joins);
        validateStructure(resolved, policy, persistentPath);
        return direction == SortDirection.ASC
            ? criteriaBuilder.asc(resolved.path())
            : criteriaBuilder.desc(resolved.path());
    }

    private Order build(
            Sorting.Order sort,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            QueryPolicy policy,
            JoinRegistry joins) {
        var resolved = pathResolver.resolve(root, policy.resolveField(sort.field()), joins);
        if (!policy.isSortAllowed(sort.field())) {
            throw rejected(QueryErrorCode.FIELD_NOT_ALLOWED, "Sort field is not allowed", sort);
        }
        validateStructure(resolved, policy, sort.field());
        return sort.direction() == SortDirection.ASC
            ? criteriaBuilder.asc(resolved.path())
            : criteriaBuilder.desc(resolved.path());
    }

    private static void validateStructure(
            JpaResolvedPath resolved,
            QueryPolicy policy,
            String field) {
        if (resolved.relationshipDepth() > 0 && !policy.relationshipTraversal()) {
            throw rejected(
                QueryErrorCode.RELATIONSHIP_TRAVERSAL_DISABLED,
                "Relationship traversal is disabled",
                field);
        }
        if (resolved.relationshipDepth() > policy.maxDepth()) {
            throw rejected(
                QueryErrorCode.RELATIONSHIP_DEPTH_EXCEEDED,
                "Sort path exceeds maximum relationship depth",
                field);
        }
        if (resolved.plural()) {
            throw rejected(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Sorting through a to-many relationship is not supported",
                field);
        }
    }

    private static QueryValidationException rejected(
            QueryErrorCode code, String message, Sorting.Order sort) {
        return rejected(code, message, sort.field());
    }

    private static QueryValidationException rejected(
            QueryErrorCode code, String message, String field) {
        return new QueryValidationException(code, message, field);
    }
}
