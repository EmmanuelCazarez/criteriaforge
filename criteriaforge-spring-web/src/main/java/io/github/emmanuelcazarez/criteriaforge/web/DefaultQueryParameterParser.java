package io.github.emmanuelcazarez.criteriaforge.web;

import io.github.emmanuelcazarez.criteriaforge.core.FilterExpression;
import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.Operator;
import io.github.emmanuelcazarez.criteriaforge.core.PageSpec;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.core.SortSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.MultiValueMap;

/** Parser for CriteriaForge's compact, backwards-friendly URL convention. */
public final class DefaultQueryParameterParser implements QueryParameterParser {
    public static final int DEFAULT_MAX_FILTER_LENGTH = 4096;
    public static final int DEFAULT_MAX_EXPRESSION_DEPTH = 20;
    private static final String OR_PREFIX = "OR_";
    private static final Set<String> CONTROL_PARAMETERS =
        Set.of("filter", "fields", "sort", "limit", "offset");
    private static final Map<String, Operator> SUFFIX_OPERATORS = suffixOperators();
    private final int maxFilterLength;
    private final int maxExpressionDepth;

    public DefaultQueryParameterParser() {
        this(DEFAULT_MAX_FILTER_LENGTH, DEFAULT_MAX_EXPRESSION_DEPTH);
    }

    public DefaultQueryParameterParser(int maxFilterLength, int maxExpressionDepth) {
        if (maxFilterLength < 1 || maxExpressionDepth < 1) {
            throw new IllegalArgumentException("parser limits must be at least one");
        }
        this.maxFilterLength = maxFilterLength;
        this.maxExpressionDepth = maxExpressionDepth;
    }

    @Override
    public QueryRequest parse(MultiValueMap<String, String> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        var normalFilters = new ArrayList<FilterExpression>();
        var alternativeFilters = new ArrayList<FilterExpression>();
        var readableFilter = oneFilterExpression(parameters.get("filter"));
        var hasSimpleFilters = parameters.keySet().stream()
            .anyMatch(name -> !CONTROL_PARAMETERS.contains(name));
        if (readableFilter != null && hasSimpleFilters) {
            throw malformed(
                "The filter expression cannot be mixed with simple filter parameters",
                "filter",
                null);
        }

        if (readableFilter == null) {
            parameters.forEach((rawName, rawValues) -> {
                if (!CONTROL_PARAMETERS.contains(rawName)) {
                    var alternative = rawName.startsWith(OR_PREFIX);
                    var name = alternative ? rawName.substring(OR_PREFIX.length()) : rawName;
                    var expression = parseFilter(name, rawValues);
                    (alternative ? alternativeFilters : normalFilters).add(expression);
                }
            });
        }

        var builder = QueryRequest.builder();
        splitValues(parameters.get("fields")).forEach(field -> select(builder, field));
        splitValues(parameters.get("sort")).forEach(token -> builder.sort(parseSort(token)));
        if (readableFilter != null) {
            builder.where(new FilterExpressionParser(
                readableFilter, maxExpressionDepth).parse());
        } else {
            combine(normalFilters, alternativeFilters).ifPresent(builder::where);
        }
        parsePage(parameters).ifPresent(builder::page);
        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw malformed("Malformed query parameters", null, exception);
        }
    }

    private String oneFilterExpression(List<String> values) {
        if (values == null) {
            return null;
        }
        if (values.size() != 1 || values.get(0) == null || values.get(0).isBlank()) {
            throw malformed("filter must appear exactly once and not be blank", "filter", null);
        }
        var expression = values.get(0);
        if (expression.length() > maxFilterLength) {
            throw malformed(
                "filter exceeds maximum length " + maxFilterLength,
                "filter",
                null);
        }
        return expression;
    }

    private static void select(QueryRequest.Builder builder, String token) {
        var match = java.util.regex.Pattern
            .compile("(?i)^(.+?)\\s+as\\s+(.+)$")
            .matcher(token);
        if (match.matches()) {
            builder.selectAs(match.group(1).trim(), match.group(2).trim());
        } else {
            builder.select(token);
        }
    }

    private static FilterExpression parseFilter(String name, List<String> rawValues) {
        if (name.isBlank()) {
            throw malformed("Filter name must not be blank", name, null);
        }
        var match = suffix(name);
        var field = match == null ? name : name.substring(0, name.length() - match.suffix().length());
        var operator = match == null ? Operator.IN : match.operator();
        if (match == null && looksLikeUnknownOperator(name)) {
            throw new QueryValidationException(
                QueryErrorCode.UNSUPPORTED_OPERATOR,
                "Unknown query operator suffix",
                name);
        }
        var values = splitValues(rawValues);
        try {
            return switch (operator) {
                case IS_NULL, IS_NOT_NULL -> nullCondition(field, operator, values);
                default -> Filters.condition(field, operator, values.toArray(String[]::new));
            };
        } catch (IllegalArgumentException exception) {
            throw malformed("Malformed filter", field, exception);
        }
    }

    private static FilterExpression nullCondition(
            String field, Operator operator, List<String> values) {
        if (values.size() != 1 || !"true".equalsIgnoreCase(values.get(0))) {
            throw new IllegalArgumentException("null operators require the value true");
        }
        return Filters.condition(field, operator);
    }

    private static java.util.Optional<FilterExpression> combine(
            List<FilterExpression> normal, List<FilterExpression> alternatives) {
        if (normal.isEmpty() && alternatives.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (alternatives.isEmpty()) {
            return java.util.Optional.of(groupAnd(normal));
        }
        var disjunction = new ArrayList<FilterExpression>();
        if (!normal.isEmpty()) {
            disjunction.add(groupAnd(normal));
        }
        disjunction.addAll(alternatives);
        return java.util.Optional.of(disjunction.size() == 1
            ? disjunction.get(0)
            : Filters.anyOf(disjunction));
    }

    private static FilterExpression groupAnd(List<FilterExpression> expressions) {
        return expressions.size() == 1
            ? expressions.get(0)
            : Filters.allOf(expressions);
    }

    private static SortSpec parseSort(String token) {
        if (token.startsWith("-")) {
            return SortSpec.desc(token.substring(1));
        }
        if (token.startsWith("+")) {
            return SortSpec.asc(token.substring(1));
        }
        return SortSpec.asc(token);
    }

    private static java.util.Optional<PageSpec> parsePage(
            MultiValueMap<String, String> parameters) {
        var limits = parameters.get("limit");
        var offsets = parameters.get("offset");
        if (limits == null && offsets == null) {
            return java.util.Optional.empty();
        }
        if (limits == null) {
            throw malformed("offset requires limit", "offset", null);
        }
        var limit = oneInteger(limits, "limit");
        var offset = offsets == null ? 0 : oneInteger(offsets, "offset");
        try {
            return java.util.Optional.of(PageSpec.offset(offset, limit));
        } catch (IllegalArgumentException exception) {
            throw malformed("Malformed pagination", "limit", exception);
        }
    }

    private static int oneInteger(List<String> values, String name) {
        if (values.size() != 1 || values.get(0) == null || values.get(0).contains(",")) {
            throw malformed(name + " must appear exactly once", name, null);
        }
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException exception) {
            throw malformed(name + " must be an integer", name, exception);
        }
    }

    private static List<String> splitValues(List<String> rawValues) {
        if (rawValues == null) {
            return List.of();
        }
        return rawValues.stream()
            .filter(Objects::nonNull)
            .flatMap(value -> java.util.Arrays.stream(value.split(",", -1)))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private static SuffixMatch suffix(String name) {
        for (var entry : SUFFIX_OPERATORS.entrySet()) {
            if (name.endsWith(entry.getKey()) && name.length() > entry.getKey().length()) {
                return new SuffixMatch(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    private static boolean looksLikeUnknownOperator(String name) {
        var separator = name.lastIndexOf('_');
        if (separator < 1 || separator == name.length() - 1) {
            return false;
        }
        var suffix = name.substring(separator + 1);
        return suffix.equals(suffix.toLowerCase(Locale.ROOT))
            && suffix.chars().allMatch(Character::isLetter);
    }

    private static Map<String, Operator> suffixOperators() {
        var operators = new LinkedHashMap<String, Operator>();
        operators.put("_notnull", Operator.IS_NOT_NULL);
        operators.put("_between", Operator.BETWEEN);
        operators.put("_isnull", Operator.IS_NULL);
        operators.put("_gte", Operator.GTE);
        operators.put("_lte", Operator.LTE);
        operators.put("_like", Operator.LIKE);
        operators.put("_not", Operator.NE);
        operators.put("_in", Operator.IN);
        operators.put("_gt", Operator.GT);
        operators.put("_lt", Operator.LT);
        operators.put("_eq", Operator.EQ);
        return Collections.unmodifiableMap(operators);
    }

    private static QueryValidationException malformed(
            String message, String path, Throwable cause) {
        return new QueryValidationException(
            QueryErrorCode.MALFORMED_QUERY,
            message,
            path,
            cause);
    }

    private record SuffixMatch(String suffix, Operator operator) {
    }
}
