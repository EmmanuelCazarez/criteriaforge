package io.github.emmanuelcazarez.criteriaforge.core;

/** Offset-based pagination requested by a dynamic query. */
public record Pagination(int offset, int limit) {

    public Pagination {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one");
        }
    }
}
