package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.FilterExpression;
import io.github.emmanuelcazarez.criteriaforge.core.Operator;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.annotation.QueryHidden;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Objects;

/** Performs a metadata-only safety preflight before Criteria query construction. */
final class JpaQueryPolicyValidator {
    private final Metamodel metamodel;
    private final OperatorCompatibility compatibility = new OperatorCompatibility();

    JpaQueryPolicyValidator(Metamodel metamodel) {
        this.metamodel = Objects.requireNonNull(metamodel, "metamodel must not be null");
    }

    void validate(Class<?> entityType, QueryRequest query, QueryPolicy policy) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        query.filter().ifPresent(expression -> validateFilter(entityType, expression, policy));
        query.fields().forEach(field -> validateProjection(entityType, field.source(), policy));
        query.sorting().orders()
            .forEach(order -> validateSort(entityType, order.field(), policy));
    }

    private void validateFilter(
            Class<?> entityType, FilterExpression expression, QueryPolicy policy) {
        expression.accept(new FilterExpression.Visitor<Void>() {
            @Override
            public Void condition(String field, Operator operator, List<Object> values) {
                var metadata = resolve(entityType, policy.resolveField(field));
                validateCommon(field, metadata, policy);
                if (!policy.isOperatorAllowed(field, operator)) {
                    throw rejected(
                        QueryErrorCode.UNSUPPORTED_OPERATOR,
                        "Operator is not allowed for this field",
                        field);
                }
                compatibility.validate(operator, metadata.javaType(), field);
                return null;
            }

            @Override
            public Void and(List<FilterExpression> expressions) {
                return validateChildren(expressions);
            }

            @Override
            public Void or(List<FilterExpression> expressions) {
                return validateChildren(expressions);
            }

            @Override
            public Void not(FilterExpression child) {
                validateFilter(entityType, child, policy);
                return null;
            }

            private Void validateChildren(List<FilterExpression> expressions) {
                expressions.forEach(child -> validateFilter(entityType, child, policy));
                return null;
            }
        });
    }

    private void validateProjection(Class<?> entityType, String field, QueryPolicy policy) {
        var metadata = resolve(entityType, policy.resolveField(field));
        validateCommon(field, metadata, policy);
        if (metadata.plural() || isManagedType(metadata.javaType())) {
            throw rejected(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Only scalar root and to-one projection fields are supported",
                field);
        }
    }

    private void validateSort(Class<?> entityType, String field, QueryPolicy policy) {
        var metadata = resolve(entityType, policy.resolveField(field));
        validateCommon(field, metadata, policy);
        if (metadata.plural()) {
            throw rejected(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Sorting through a to-many relationship is not supported",
                field);
        }
    }

    private void validateCommon(String field, Metadata metadata, QueryPolicy policy) {
        if (metadata.hidden() || !policy.isFieldAllowed(field)) {
            throw rejected(QueryErrorCode.FIELD_NOT_ALLOWED, "Field is not queryable", field);
        }
        if (metadata.relationshipDepth() > 0 && !policy.relationshipTraversal()) {
            throw rejected(
                QueryErrorCode.RELATIONSHIP_TRAVERSAL_DISABLED,
                "Relationship traversal is disabled",
                field);
        }
        if (metadata.relationshipDepth() > policy.maxDepth()) {
            throw rejected(
                QueryErrorCode.RELATIONSHIP_DEPTH_EXCEEDED,
                "Relationship path exceeds maximum depth " + policy.maxDepth(),
                field);
        }
    }

    private Metadata resolve(Class<?> entityType, String path) {
        ManagedType<?> currentType;
        try {
            currentType = metamodel.managedType(entityType);
        } catch (IllegalArgumentException exception) {
            throw unknown(path, exception);
        }

        var plural = false;
        var relationshipDepth = 0;
        var hidden = false;
        Class<?> javaType = entityType;
        var segments = path.split("\\.", -1);
        for (int index = 0; index < segments.length; index++) {
            Attribute<?, ?> attribute;
            try {
                attribute = currentType.getAttribute(segments[index]);
            } catch (IllegalArgumentException exception) {
                throw unknown(path, exception);
            }
            hidden |= isHidden(attribute);
            var pluralAttribute = attribute instanceof PluralAttribute;
            plural |= pluralAttribute;
            if (attribute.isAssociation() || pluralAttribute) {
                relationshipDepth++;
            }
            javaType = javaType(attribute);
            if (index < segments.length - 1) {
                try {
                    currentType = metamodel.managedType(javaType);
                } catch (IllegalArgumentException exception) {
                    throw unknown(path, exception);
                }
            }
        }
        return new Metadata(javaType, plural, relationshipDepth, hidden);
    }

    private static boolean isHidden(Attribute<?, ?> attribute) {
        return attribute.getJavaMember() instanceof AnnotatedElement element
            && element.isAnnotationPresent(QueryHidden.class);
    }

    private static Class<?> javaType(Attribute<?, ?> attribute) {
        if (attribute instanceof PluralAttribute<?, ?, ?> plural) {
            return plural.getElementType().getJavaType();
        }
        return attribute.getJavaType();
    }

    private boolean isManagedType(Class<?> type) {
        try {
            metamodel.managedType(type);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static QueryValidationException unknown(String path, Throwable cause) {
        return new QueryValidationException(
            QueryErrorCode.UNKNOWN_FIELD,
            "Unknown persistent field path",
            path,
            cause);
    }

    private static QueryValidationException rejected(
            QueryErrorCode code, String message, String field) {
        return new QueryValidationException(code, message, field);
    }

    private record Metadata(
        Class<?> javaType, boolean plural, int relationshipDepth, boolean hidden) {
    }
}
