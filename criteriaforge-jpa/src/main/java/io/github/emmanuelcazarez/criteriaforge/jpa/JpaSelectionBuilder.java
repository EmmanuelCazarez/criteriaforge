package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.ProjectionField;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.Metamodel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves and validates dynamic scalar projection selections. */
final class JpaSelectionBuilder {
    private final JpaPathResolver pathResolver;
    private final Metamodel metamodel;

    JpaSelectionBuilder(JpaPathResolver pathResolver, Metamodel metamodel) {
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
        this.metamodel = Objects.requireNonNull(metamodel, "metamodel must not be null");
    }

    List<Selection<?>> build(
            List<ProjectionField> fields, Root<?> root, QueryPolicy policy, JoinRegistry joins) {
        var selections = new ArrayList<Selection<?>>();
        fields.forEach(field -> selections.add(selection(field, root, policy, joins)));
        return List.copyOf(selections);
    }

    private Selection<?> selection(
            ProjectionField projection, Root<?> root, QueryPolicy policy, JoinRegistry joins) {
        var field = projection.source();
        var resolved = pathResolver.resolve(root, policy.resolveField(field), joins);
        if (!policy.isFieldAllowed(field)) {
            throw rejected(QueryErrorCode.FIELD_NOT_ALLOWED, "Projection field is not allowed", field);
        }
        if (resolved.relationshipDepth() > 0 && !policy.relationshipTraversal()) {
            throw rejected(
                QueryErrorCode.RELATIONSHIP_TRAVERSAL_DISABLED,
                "Relationship traversal is disabled",
                field);
        }
        if (resolved.relationshipDepth() > policy.maxDepth()) {
            throw rejected(
                QueryErrorCode.RELATIONSHIP_DEPTH_EXCEEDED,
                "Projection path exceeds maximum relationship depth",
                field);
        }
        if (resolved.plural() || isManagedType(resolved.javaType())) {
            throw rejected(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Only scalar root and to-one projection fields are supported",
                field);
        }
        return resolved.path().alias(projection.output());
    }

    private boolean isManagedType(Class<?> type) {
        try {
            metamodel.managedType(type);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static QueryValidationException rejected(
            QueryErrorCode code, String message, String field) {
        return new QueryValidationException(code, message, field);
    }
}
