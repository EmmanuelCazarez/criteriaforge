package io.github.emmanuelcazarez.criteriaforge.jpa;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reuses joins by their complete logical path within one Criteria query. */
final class JoinRegistry {
    private final From<?, ?> root;
    private final Map<String, From<?, ?>> joins = new LinkedHashMap<>();

    public JoinRegistry(From<?, ?> root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    From<?, ?> join(From<?, ?> parent, String attribute, String completePath) {
        Objects.requireNonNull(parent, "parent must not be null");
        Objects.requireNonNull(attribute, "attribute must not be null");
        Objects.requireNonNull(completePath, "completePath must not be null");
        return joins.computeIfAbsent(
            completePath,
            ignored -> parent.join(attribute, JoinType.LEFT));
    }

    From<?, ?> root() {
        return root;
    }
}
