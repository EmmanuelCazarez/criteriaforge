package io.github.emmanuelcazarez.criteriaforge.core;

/** Offset-based pagination requested by a dynamic query. */
public record PageSpec(int offset, int limit) {

    public PageSpec {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one");
        }
    }

    public static PageSpec offset(int offset, int limit) {
        return new PageSpec(offset, limit);
    }
}
