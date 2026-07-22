package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.Objects;

/** Resolves dotted query fields with the JPA metamodel instead of string assumptions. */
final class JpaPathResolver {
    private final Metamodel metamodel;

    public JpaPathResolver(Metamodel metamodel) {
        this.metamodel = Objects.requireNonNull(metamodel, "metamodel must not be null");
    }

    public JpaResolvedPath resolve(Root<?> root, String logicalPath, JoinRegistry joins) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(joins, "joins must not be null");
        if (logicalPath == null || logicalPath.isBlank()) {
            throw unknownPath(logicalPath);
        }

        var segments = logicalPath.split("\\.", -1);
        Path<?> currentPath = root;
        From<?, ?> currentFrom = root;
        ManagedType<?> currentType = metamodel.managedType(root.getJavaType());
        var plural = false;
        var relationshipDepth = 0;
        var traversed = new StringBuilder();
        Class<?> resolvedType = root.getJavaType();

        for (int index = 0; index < segments.length; index++) {
            var segment = segments[index];
            if (segment.isBlank()) {
                throw unknownPath(logicalPath);
            }
            if (!traversed.isEmpty()) {
                traversed.append('.');
            }
            traversed.append(segment);

            var attribute = attribute(currentType, segment, logicalPath);
            var association = attribute.isAssociation() || attribute instanceof PluralAttribute;
            var pluralAttribute = attribute instanceof PluralAttribute;
            plural |= pluralAttribute;
            if (association) {
                relationshipDepth++;
            }
            resolvedType = javaType(attribute);

            var hasMoreSegments = index < segments.length - 1;
            if (association && currentPath instanceof From<?, ?> from) {
                currentFrom = joins.join(from, segment, traversed.toString());
                currentPath = currentFrom;
            } else {
                currentPath = currentPath.get(segment);
            }

            if (hasMoreSegments) {
                currentType = managedType(resolvedType, logicalPath);
                if (currentPath instanceof From<?, ?> from) {
                    currentFrom = from;
                }
            }
        }

        return new JpaResolvedPath(currentPath, resolvedType, plural, relationshipDepth);
    }

    private static Attribute<?, ?> attribute(
            ManagedType<?> managedType, String segment, String logicalPath) {
        try {
            return managedType.getAttribute(segment);
        } catch (IllegalArgumentException exception) {
            throw unknownPath(logicalPath, exception);
        }
    }

    private ManagedType<?> managedType(Class<?> type, String logicalPath) {
        try {
            return metamodel.managedType(type);
        } catch (IllegalArgumentException exception) {
            throw unknownPath(logicalPath, exception);
        }
    }

    private static Class<?> javaType(Attribute<?, ?> attribute) {
        if (attribute instanceof PluralAttribute<?, ?, ?> pluralAttribute) {
            return pluralAttribute.getElementType().getJavaType();
        }
        return attribute.getJavaType();
    }

    private static QueryValidationException unknownPath(String path) {
        return unknownPath(path, null);
    }

    private static QueryValidationException unknownPath(String path, Throwable cause) {
        return new QueryValidationException(
            QueryErrorCode.UNKNOWN_FIELD,
            "Unknown persistent field path",
            path,
            cause);
    }
}
