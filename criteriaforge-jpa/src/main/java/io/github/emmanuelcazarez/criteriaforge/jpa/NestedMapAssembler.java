package io.github.emmanuelcazarez.criteriaforge.jpa;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reconstructs ordered nested maps from flat Criteria tuple selections. */
public final class NestedMapAssembler {

    public Map<String, Object> assemble(List<String> paths, List<?> values) {
        Objects.requireNonNull(paths, "paths must not be null");
        Objects.requireNonNull(values, "values must not be null");
        if (paths.size() != values.size()) {
            throw new IllegalArgumentException("paths and values must have the same size");
        }

        var root = new LinkedHashMap<String, Object>();
        for (int index = 0; index < paths.size(); index++) {
            insert(root, paths.get(index), values.get(index));
        }
        return immutable(root);
    }

    private static void insert(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("projection path must not be blank");
        }
        var segments = path.split("\\.", -1);
        Map<String, Object> current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            var segment = segments[index];
            var existing = current.get(segment);
            if (existing == null && !current.containsKey(segment)) {
                var nested = new LinkedHashMap<String, Object>();
                current.put(segment, nested);
                current = nested;
            } else if (existing instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                var nested = (Map<String, Object>) map;
                current = nested;
            } else {
                throw new IllegalArgumentException("projection path collides at " + segment);
            }
        }

        var leaf = segments[segments.length - 1];
        if (leaf.isBlank() || current.containsKey(leaf)) {
            throw new IllegalArgumentException("duplicate or invalid projection path " + path);
        }
        current.put(leaf, value);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                var nested = (Map<String, Object>) map;
                copy.put(key, immutable(nested));
            } else {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
