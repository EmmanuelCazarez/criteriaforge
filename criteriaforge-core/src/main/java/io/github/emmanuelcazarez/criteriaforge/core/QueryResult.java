package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;
import java.util.Objects;

/** Immutable query content and offset-pagination metadata. */
public record QueryResult<T>(List<T> content, long total, int offset, int limit) {

    public QueryResult {
        content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one");
        }
        if (content.size() > limit) {
            throw new IllegalArgumentException("content size must not exceed limit");
        }
    }
}
