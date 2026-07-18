package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, transport-neutral description of a dynamic query. */
public record QuerySpec(
        List<String> fields,
        Optional<FilterExpression> filter,
        List<SortSpec> sorts,
        Optional<PageSpec> page) {

    public QuerySpec {
        fields = validateFields(fields);
        filter = Objects.requireNonNull(filter, "filter must not be null");
        sorts = List.copyOf(Objects.requireNonNull(sorts, "sorts must not be null"));
        if (sorts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sorts must not contain null");
        }
        page = Objects.requireNonNull(page, "page must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<String> validateFields(List<String> requestedFields) {
        Objects.requireNonNull(requestedFields, "fields must not be null");
        var validated = requestedFields.stream()
            .map(field -> QueryPath.requireValid(field, "projection field"))
            .toList();
        if (new LinkedHashSet<>(validated).size() != validated.size()) {
            throw new IllegalArgumentException("projection fields must not contain duplicates");
        }
        return List.copyOf(validated);
    }

    /** Mutable builder that creates an immutable {@link QuerySpec}. */
    public static final class Builder {
        private final List<String> fields = new ArrayList<>();
        private final List<SortSpec> sorts = new ArrayList<>();
        private FilterExpression filter;
        private PageSpec page;

        private Builder() {
        }

        public Builder select(String... fields) {
            return select(Arrays.asList(fields));
        }

        public Builder select(Collection<String> fields) {
            this.fields.addAll(Objects.requireNonNull(fields, "fields must not be null"));
            return this;
        }

        public Builder where(FilterExpression filter) {
            this.filter = Objects.requireNonNull(filter, "filter must not be null");
            return this;
        }

        public Builder sort(SortSpec... sorts) {
            this.sorts.addAll(Arrays.asList(sorts));
            return this;
        }

        public Builder page(PageSpec page) {
            this.page = Objects.requireNonNull(page, "page must not be null");
            return this;
        }

        public QuerySpec build() {
            return new QuerySpec(
                fields,
                Optional.ofNullable(filter),
                sorts,
                Optional.ofNullable(page));
        }
    }
}
