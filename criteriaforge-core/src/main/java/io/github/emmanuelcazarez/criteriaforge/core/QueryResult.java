package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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

    /** Maps each content element while preserving offset-pagination metadata. */
    public <R> QueryResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new QueryResult<>(content.stream().<R>map(mapper).toList(), total, offset, limit);
    }

    /** Returns whether results exist after this offset window. */
    public boolean hasNext() {
        return (long) offset + content.size() < total;
    }

    /** Returns whether results exist before this offset window. */
    public boolean hasPrevious() {
        return offset > 0 && total > 0;
    }
}
