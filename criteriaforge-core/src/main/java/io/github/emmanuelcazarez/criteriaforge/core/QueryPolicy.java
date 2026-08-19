package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable limits and field permissions applied to a dynamic query. */
public final class QueryPolicy {
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_MAX_CONDITIONS = 25;
    public static final int DEFAULT_MAX_DEPTH = 2;
    public static final int DEFAULT_MAX_PROJECTION_FIELDS = 20;
    public static final int DEFAULT_MAX_SORT_FIELDS = 5;

    private final int maxPageSize;
    private final int maxConditions;
    private final int maxDepth;
    private final int maxProjectionFields;
    private final int maxSortFields;
    private final boolean relationshipTraversal;
    private final boolean projectionAllowlistEnabled;
    private final boolean filterAllowlistEnabled;
    private final boolean sortAllowlistEnabled;
    private final Set<String> projectionFields;
    private final Set<String> filterFields;
    private final Set<String> sortFields;
    private final Set<String> allowedFields;
    private final Set<String> deniedFields;
    private final Map<String, Set<Operator>> allowedOperators;
    private final Map<String, String> aliases;
    private final Optional<Sorting.Order> tieBreaker;

    private QueryPolicy(Builder builder) {
        maxPageSize = requirePositive(builder.maxPageSize, "maxPageSize");
        maxConditions = requirePositive(builder.maxConditions, "maxConditions");
        maxDepth = requireNonNegative(builder.maxDepth, "maxDepth");
        maxProjectionFields = requirePositive(
            builder.maxProjectionFields, "maxProjectionFields");
        maxSortFields = requirePositive(builder.maxSortFields, "maxSortFields");
        relationshipTraversal = builder.relationshipTraversal;
        aliases = immutableAliases(builder.aliases);
        if (builder.permissionMode == Builder.PermissionMode.UNIFIED) {
            var unifiedFields = new LinkedHashSet<>(builder.allowedFields);
            unifiedFields.addAll(aliases.keySet());
            projectionFields = immutablePaths(unifiedFields, "projection field");
            filterFields = projectionFields;
            sortFields = projectionFields;
            var enabled = !builder.allowedFields.isEmpty();
            projectionAllowlistEnabled = enabled;
            filterAllowlistEnabled = enabled;
            sortAllowlistEnabled = enabled;
        } else {
            projectionFields = immutablePaths(
                builder.projectionFields, "projection field");
            filterFields = immutablePaths(builder.filterFields, "filter field");
            sortFields = immutablePaths(builder.sortFields, "sort field");
            projectionAllowlistEnabled = builder.projectionFieldsConfigured;
            filterAllowlistEnabled = builder.filterFieldsConfigured;
            sortAllowlistEnabled = builder.sortFieldsConfigured;
        }
        allowedFields = builder.permissionMode == Builder.PermissionMode.UNIFIED
            ? projectionFields
            : commonAllowedFields();
        deniedFields = immutablePaths(builder.deniedFields, "denied field");
        allowedOperators = immutableOperators(builder.allowedOperators);
        tieBreaker = Optional.ofNullable(builder.tieBreaker);
    }

    public static QueryPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int maxPageSize() {
        return maxPageSize;
    }

    public int maxConditions() {
        return maxConditions;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int maxProjectionFields() {
        return maxProjectionFields;
    }

    public int maxSortFields() {
        return maxSortFields;
    }

    public boolean relationshipTraversal() {
        return relationshipTraversal;
    }

    public Set<String> projectionFields() {
        return projectionFields;
    }

    public Set<String> filterFields() {
        return filterFields;
    }

    public Set<String> sortFields() {
        return sortFields;
    }

    /**
     * Returns fields explicitly allowed for all query operations.
     *
     * @deprecated use the operation-specific field accessors
     */
    @Deprecated(since = "0.2.0")
    public Set<String> allowedFields() {
        return allowedFields;
    }

    public Set<String> deniedFields() {
        return deniedFields;
    }

    public Map<String, Set<Operator>> allowedOperators() {
        return allowedOperators;
    }

    public Map<String, String> aliases() {
        return aliases;
    }

    /** Returns the configured tie-breaker override, or empty for the JPA identifier default. */
    public Optional<Sorting.Order> tieBreaker() {
        return tieBreaker;
    }

    public boolean isProjectionAllowed(String field) {
        return isAllowed(field, projectionAllowlistEnabled, projectionFields);
    }

    public boolean isFilterAllowed(String field) {
        return isAllowed(field, filterAllowlistEnabled, filterFields);
    }

    public boolean isSortAllowed(String field) {
        return isAllowed(field, sortAllowlistEnabled, sortFields);
    }

    /**
     * Returns whether every query operation allows {@code field}.
     *
     * @deprecated use the operation-specific permission checks
     */
    @Deprecated(since = "0.2.0")
    public boolean isFieldAllowed(String field) {
        return isProjectionAllowed(field)
            && isFilterAllowed(field)
            && isSortAllowed(field);
    }

    public boolean isOperatorAllowed(String field, Operator operator) {
        Objects.requireNonNull(operator, "operator must not be null");
        var configured = allowedOperators.get(QueryPath.requireValid(field, "field"));
        return configured == null || configured.contains(operator);
    }

    /** Resolves a public query field to its persistent JPA path. */
    public String resolveField(String field) {
        var normalized = QueryPath.requireValid(field, "field");
        return aliases.getOrDefault(normalized, normalized);
    }

    private boolean isAllowed(String field, boolean configured, Set<String> fields) {
        var normalized = QueryPath.requireValid(field, "field");
        return !deniedFields.contains(normalized)
            && (!configured || fields.contains(normalized));
    }

    private Set<String> commonAllowedFields() {
        LinkedHashSet<String> common = null;
        if (projectionAllowlistEnabled) {
            common = new LinkedHashSet<>(projectionFields);
        }
        if (filterAllowlistEnabled) {
            common = intersect(common, filterFields);
        }
        if (sortAllowlistEnabled) {
            common = intersect(common, sortFields);
        }
        return common == null ? Set.of() : Set.copyOf(common);
    }

    private static LinkedHashSet<String> intersect(
            LinkedHashSet<String> current, Set<String> fields) {
        if (current == null) {
            return new LinkedHashSet<>(fields);
        }
        current.retainAll(fields);
        return current;
    }

    private static int requirePositive(int value, String label) {
        if (value < 1) {
            throw new IllegalArgumentException(label + " must be at least one");
        }
        return value;
    }

    private static int requireNonNegative(int value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    private static Set<String> immutablePaths(Collection<String> paths, String label) {
        var validated = new LinkedHashSet<String>();
        for (String path : paths) {
            validated.add(QueryPath.requireValid(path, label));
        }
        return Set.copyOf(validated);
    }

    private static Map<String, Set<Operator>> immutableOperators(
            Map<String, Set<Operator>> configured) {
        var copy = new LinkedHashMap<String, Set<Operator>>();
        configured.forEach((field, operators) -> copy.put(
            QueryPath.requireValid(field, "operator field"),
            Set.copyOf(operators)));
        return Map.copyOf(copy);
    }

    private static Map<String, String> immutableAliases(Map<String, String> configured) {
        var copy = new LinkedHashMap<String, String>();
        configured.forEach((publicName, persistentPath) -> copy.put(
            QueryPath.requireValid(publicName, "public field"),
            QueryPath.requireValid(persistentPath, "persistent field")));
        return Map.copyOf(copy);
    }

    /** Builder for per-entity or global query policies. */
    public static final class Builder {
        private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
        private int maxConditions = DEFAULT_MAX_CONDITIONS;
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private int maxProjectionFields = DEFAULT_MAX_PROJECTION_FIELDS;
        private int maxSortFields = DEFAULT_MAX_SORT_FIELDS;
        private boolean relationshipTraversal;
        private PermissionMode permissionMode = PermissionMode.NONE;
        private boolean projectionFieldsConfigured;
        private boolean filterFieldsConfigured;
        private boolean sortFieldsConfigured;
        private final Set<String> projectionFields = new LinkedHashSet<>();
        private final Set<String> filterFields = new LinkedHashSet<>();
        private final Set<String> sortFields = new LinkedHashSet<>();
        private final Set<String> allowedFields = new LinkedHashSet<>();
        private final Set<String> deniedFields = new LinkedHashSet<>();
        private final Map<String, Set<Operator>> allowedOperators = new LinkedHashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private Sorting.Order tieBreaker;

        private Builder() {
        }

        public Builder maxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
            return this;
        }

        public Builder maxConditions(int maxConditions) {
            this.maxConditions = maxConditions;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder maxProjectionFields(int maxProjectionFields) {
            this.maxProjectionFields = maxProjectionFields;
            return this;
        }

        public Builder maxSortFields(int maxSortFields) {
            this.maxSortFields = maxSortFields;
            return this;
        }

        public Builder relationshipTraversal(boolean relationshipTraversal) {
            this.relationshipTraversal = relationshipTraversal;
            return this;
        }

        public Builder tieBreaker(String field) {
            return tieBreaker(field, SortDirection.ASC);
        }

        /**
         * Overrides the primary-key default with a field that must uniquely order matching rows.
         */
        public Builder tieBreaker(String field, SortDirection direction) {
            tieBreaker = new Sorting.Order(field, direction);
            return this;
        }

        /**
         * Configures one allowlist for projection, filtering, and sorting.
         *
         * @deprecated use the operation-specific allowlist methods
         */
        @Deprecated(since = "0.2.0")
        public Builder allowFields(String... fields) {
            return allowFields(Arrays.asList(fields));
        }

        /**
         * Configures one allowlist for projection, filtering, and sorting.
         *
         * @deprecated use the operation-specific allowlist methods
         */
        @Deprecated(since = "0.2.0")
        public Builder allowFields(Collection<String> fields) {
            usePermissionMode(PermissionMode.UNIFIED);
            allowedFields.addAll(Objects.requireNonNull(fields, "fields must not be null"));
            return this;
        }

        public Builder allowProjectionFields(String... fields) {
            return allowProjectionFields(Arrays.asList(fields));
        }

        public Builder allowProjectionFields(Collection<String> fields) {
            usePermissionMode(PermissionMode.OPERATION_SPECIFIC);
            projectionFieldsConfigured = true;
            projectionFields.addAll(Objects.requireNonNull(fields, "fields must not be null"));
            return this;
        }

        public Builder allowFilterFields(String... fields) {
            return allowFilterFields(Arrays.asList(fields));
        }

        public Builder allowFilterFields(Collection<String> fields) {
            usePermissionMode(PermissionMode.OPERATION_SPECIFIC);
            filterFieldsConfigured = true;
            filterFields.addAll(Objects.requireNonNull(fields, "fields must not be null"));
            return this;
        }

        public Builder allowSortFields(String... fields) {
            return allowSortFields(Arrays.asList(fields));
        }

        public Builder allowSortFields(Collection<String> fields) {
            usePermissionMode(PermissionMode.OPERATION_SPECIFIC);
            sortFieldsConfigured = true;
            sortFields.addAll(Objects.requireNonNull(fields, "fields must not be null"));
            return this;
        }

        public Builder denyFields(String... fields) {
            deniedFields.addAll(Arrays.asList(fields));
            return this;
        }

        public Builder allowOperators(String field, Operator... operators) {
            Objects.requireNonNull(operators, "operators must not be null");
            allowedOperators.put(field, Set.copyOf(Arrays.asList(operators)));
            return this;
        }

        public Builder alias(String publicName, String persistentPath) {
            var normalizedPublicName = QueryPath.requireValid(publicName, "public field");
            var normalizedPersistentPath =
                QueryPath.requireValid(persistentPath, "persistent field");
            if (aliases.putIfAbsent(normalizedPublicName, normalizedPersistentPath) != null) {
                throw new IllegalArgumentException(
                    "public field alias is already registered: " + normalizedPublicName);
            }
            return this;
        }

        public QueryPolicy build() {
            return new QueryPolicy(this);
        }

        private void usePermissionMode(PermissionMode requestedMode) {
            if (permissionMode != PermissionMode.NONE && permissionMode != requestedMode) {
                throw new IllegalStateException(
                    "unified and operation-specific field permissions must not be mixed");
            }
            permissionMode = requestedMode;
        }

        private enum PermissionMode {
            NONE,
            UNIFIED,
            OPERATION_SPECIFIC
        }
    }
}
