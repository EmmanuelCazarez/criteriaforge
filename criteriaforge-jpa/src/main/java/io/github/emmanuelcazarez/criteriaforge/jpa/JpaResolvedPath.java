package io.github.emmanuelcazarez.criteriaforge.jpa;

import jakarta.persistence.criteria.Path;
import java.util.Objects;

/** JPA path metadata resolved from a logical dotted field path. */
record JpaResolvedPath(
        Path<?> path, Class<?> javaType, boolean plural, int relationshipDepth) {

    public JpaResolvedPath {
        path = Objects.requireNonNull(path, "path must not be null");
        javaType = Objects.requireNonNull(javaType, "javaType must not be null");
        if (relationshipDepth < 0) {
            throw new IllegalArgumentException("relationshipDepth must not be negative");
        }
    }
}
