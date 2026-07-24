package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, transport-neutral dynamic query request. */
public record QueryRequest(
        List<ProjectionField> fields,
        Optional<FilterExpression> filter,
        List<SortSpec> sorts,
        Optional<PageSpec> page) {

    public QueryRequest {
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

    private static List<ProjectionField> validateFields(List<ProjectionField> requestedFields) {
        Objects.requireNonNull(requestedFields, "fields must not be null");
        var validated = requestedFields.stream()
            .map(field -> Objects.requireNonNull(field, "fields must not contain null"))
            .toList();
        var sources = validated.stream().map(ProjectionField::source).toList();
        if (new LinkedHashSet<>(sources).size() != sources.size()) {
            throw new IllegalArgumentException("projection sources must not contain duplicates");
        }
        validateOutputPaths(validated);
        return List.copyOf(validated);
    }

    private static void validateOutputPaths(List<ProjectionField> fields) {
        var outputs = fields.stream().map(ProjectionField::output).toList();
        if (new LinkedHashSet<>(outputs).size() != outputs.size()) {
            throw new IllegalArgumentException("projection output paths must not contain duplicates");
        }
        for (int left = 0; left < outputs.size(); left++) {
            for (int right = left + 1; right < outputs.size(); right++) {
                if (isParent(outputs.get(left), outputs.get(right))
                        || isParent(outputs.get(right), outputs.get(left))) {
                    throw new IllegalArgumentException("projection output paths must not collide");
                }
            }
        }
    }

    private static boolean isParent(String possibleParent, String possibleChild) {
        return possibleChild.startsWith(possibleParent + ".");
    }

    /** Mutable builder that creates an immutable {@link QueryRequest}. */
    public static final class Builder {
        private final List<ProjectionField> fields = new ArrayList<>();
        private final List<SortSpec> sorts = new ArrayList<>();
        private FilterExpression filter;
        private PageSpec page;

        private Builder() {
        }

        public Builder select(String... fields) {
            return select(Arrays.asList(fields));
        }

        public Builder select(Collection<String> fields) {
            Objects.requireNonNull(fields, "fields must not be null")
                .stream()
                .map(ProjectionField::of)
                .forEach(this.fields::add);
            return this;
        }

        public Builder selectAs(String source, String output) {
            this.fields.add(ProjectionField.aliased(source, output));
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

        public QueryRequest build() {
            return new QueryRequest(
                fields,
                Optional.ofNullable(filter),
                sorts,
                Optional.ofNullable(page));
        }
    }
}
