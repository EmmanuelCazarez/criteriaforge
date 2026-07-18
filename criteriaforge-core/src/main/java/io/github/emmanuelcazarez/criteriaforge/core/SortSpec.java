package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;

/** One ordered field in a query sort specification. */
public record SortSpec(String field, SortDirection direction) {

    public SortSpec {
        field = QueryPath.requireValid(field, "sort field");
        direction = Objects.requireNonNull(direction, "sort direction must not be null");
    }

    public static SortSpec asc(String field) {
        return new SortSpec(field, SortDirection.ASC);
    }

    public static SortSpec desc(String field) {
        return new SortSpec(field, SortDirection.DESC);
    }
}
