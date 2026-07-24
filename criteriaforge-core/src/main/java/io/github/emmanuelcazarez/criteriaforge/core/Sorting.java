package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.List;
import java.util.Objects;

/** Ordered sorting instructions requested by a dynamic query. */
public record Sorting(List<Order> orders) {

    public Sorting {
        Objects.requireNonNull(orders, "orders must not be null");
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("orders must not be empty");
        }
        if (orders.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("orders must not contain null");
        }
        orders = List.copyOf(orders);
    }

    /** One field and direction within the complete sorting instruction. */
    public record Order(String field, SortDirection direction) {

        public Order {
            field = QueryPath.requireValid(field, "sort field");
            direction = Objects.requireNonNull(
                direction, "sort direction must not be null");
        }
    }
}
