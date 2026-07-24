package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable limits and field permissions applied to a dynamic query. */
public final class QueryPolicy {
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_MAX_CONDITIONS = 25;
    public static final int DEFAULT_MAX_DEPTH = 2;

    private final int maxPageSize;
    private final int maxConditions;
    private final int maxDepth;
    private final boolean relationshipTraversal;
    private final boolean allowlistEnabled;
    private final Set<String> allowedFields;
    private final Set<String> deniedFields;
    private final Map<String, Set<Operator>> allowedOperators;
    private final Map<String, String> aliases;

    private QueryPolicy(Builder builder) {
        maxPageSize = requirePositive(builder.maxPageSize, "maxPageSize");
        maxConditions = requirePositive(builder.maxConditions, "maxConditions");
        maxDepth = requireNonNegative(builder.maxDepth, "maxDepth");
        relationshipTraversal = builder.relationshipTraversal;
        allowlistEnabled = !builder.allowedFields.isEmpty();
        aliases = immutableAliases(builder.aliases);
        var exposedFields = new LinkedHashSet<>(builder.allowedFields);
        exposedFields.addAll(aliases.keySet());
        allowedFields = immutablePaths(exposedFields, "allowed field");
        deniedFields = immutablePaths(builder.deniedFields, "denied field");
        allowedOperators = immutableOperators(builder.allowedOperators);
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

    public boolean relationshipTraversal() {
        return relationshipTraversal;
    }

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

    public boolean isFieldAllowed(String field) {
        var normalized = QueryPath.requireValid(field, "field");
        return !deniedFields.contains(normalized)
            && (!allowlistEnabled || allowedFields.contains(normalized));
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
        private boolean relationshipTraversal;
        private final Set<String> allowedFields = new LinkedHashSet<>();
        private final Set<String> deniedFields = new LinkedHashSet<>();
        private final Map<String, Set<Operator>> allowedOperators = new LinkedHashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();

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

        public Builder relationshipTraversal(boolean relationshipTraversal) {
            this.relationshipTraversal = relationshipTraversal;
            return this;
        }

        public Builder allowFields(String... fields) {
            return allowFields(Arrays.asList(fields));
        }

        public Builder allowFields(Collection<String> fields) {
            allowedFields.addAll(Objects.requireNonNull(fields, "fields must not be null"));
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
    }
}
