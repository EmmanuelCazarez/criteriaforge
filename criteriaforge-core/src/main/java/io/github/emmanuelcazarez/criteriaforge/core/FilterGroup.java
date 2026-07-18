package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;
import java.util.Objects;

/** An immutable AND or OR group in a filter expression tree. */
public record FilterGroup(Junction junction, List<FilterExpression> children)
        implements FilterExpression {

    public FilterGroup {
        junction = Objects.requireNonNull(junction, "junction must not be null");
        children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
        if (children.size() < 2 || children.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("filter groups require at least two expressions");
        }
    }

    /** Boolean junction used to combine child expressions. */
    public enum Junction {
        AND,
        OR
    }
}
