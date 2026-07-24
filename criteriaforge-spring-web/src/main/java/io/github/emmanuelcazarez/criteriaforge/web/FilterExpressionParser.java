package io.github.emmanuelcazarez.criteriaforge.web;

import io.github.emmanuelcazarez.criteriaforge.core.FilterExpression;
import io.github.emmanuelcazarez.criteriaforge.core.Filters;
import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Recursive-descent parser for CriteriaForge's readable HTTP filter expression. */
final class FilterExpressionParser {
    private final List<Token> tokens;
    private final int maxDepth;
    private int current;

    FilterExpressionParser(String source, int maxDepth) {
        this.tokens = new Lexer(source).tokenize();
        this.maxDepth = maxDepth;
    }

    FilterExpression parse() {
        var expression = parseOr(0);
        expect(TokenType.END, "Unexpected token after filter expression");
        return expression;
    }

    private FilterExpression parseOr(int depth) {
        var expressions = new ArrayList<FilterExpression>();
        expressions.add(parseAnd(depth));
        while (matchKeyword("or")) {
            expressions.add(parseAnd(depth));
        }
        return expressions.size() == 1
            ? expressions.get(0)
            : Filters.anyOf(expressions);
    }

    private FilterExpression parseAnd(int depth) {
        var expressions = new ArrayList<FilterExpression>();
        expressions.add(parseUnary(depth));
        while (matchKeyword("and")) {
            expressions.add(parseUnary(depth));
        }
        return expressions.size() == 1
            ? expressions.get(0)
            : Filters.allOf(expressions);
    }

    private FilterExpression parseUnary(int depth) {
        if (matchKeyword("not")) {
            checkDepth(depth + 1);
            return parseUnary(depth + 1).not();
        }
        if (match(TokenType.LEFT_PAREN)) {
            checkDepth(depth + 1);
            var expression = parseOr(depth + 1);
            expect(TokenType.RIGHT_PAREN, "Expected ')' after grouped expression");
            return expression;
        }
        return parseComparison();
    }

    private FilterExpression parseComparison() {
        var field = expect(TokenType.WORD, "Expected a field path").text();
        var operator = advance();
        try {
            return switch (operator.type()) {
                case EQ -> Filters.field(field).eq(value());
                case NE -> Filters.field(field).ne(value());
                case GT -> Filters.field(field).gt(value());
                case GTE -> Filters.field(field).gte(value());
                case LT -> Filters.field(field).lt(value());
                case LTE -> Filters.field(field).lte(value());
                case WORD -> keywordComparison(field, operator);
                default -> throw error("Expected a comparison operator", operator);
            };
        } catch (IllegalArgumentException exception) {
            throw malformed("Malformed comparison at position " + operator.position(), exception);
        }
    }

    private FilterExpression keywordComparison(String field, Token operator) {
        return switch (operator.text().toLowerCase(Locale.ROOT)) {
            case "like" -> Filters.field(field).like(value());
            case "in" -> membership(field);
            case "between" -> between(field);
            case "is" -> nullComparison(field);
            default -> throw error("Unknown comparison operator", operator);
        };
    }

    private FilterExpression membership(String field) {
        expect(TokenType.LEFT_PAREN, "Expected '(' after 'in'");
        var values = new ArrayList<Object>();
        values.add(value());
        while (match(TokenType.COMMA)) {
            values.add(value());
        }
        expect(TokenType.RIGHT_PAREN, "Expected ')' after 'in' values");
        return Filters.field(field).in(values);
    }

    private FilterExpression between(String field) {
        var lower = value();
        expectKeyword("and", "Expected 'and' between range values");
        return Filters.field(field).between(lower, value());
    }

    private FilterExpression nullComparison(String field) {
        var negated = matchKeyword("not");
        expectKeyword("null", "Expected 'null' after 'is'");
        return negated
            ? Filters.field(field).isNotNull()
            : Filters.field(field).isNull();
    }

    private String value() {
        if (check(TokenType.WORD) || check(TokenType.STRING)) {
            return advance().text();
        }
        throw error("Expected a comparison value", peek());
    }

    private boolean matchKeyword(String expected) {
        if (check(TokenType.WORD)
                && peek().text().equalsIgnoreCase(expected)) {
            advance();
            return true;
        }
        return false;
    }

    private void expectKeyword(String expected, String message) {
        if (!matchKeyword(expected)) {
            throw error(message, peek());
        }
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenType type, String message) {
        if (!check(type)) {
            throw error(message, peek());
        }
        return advance();
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token advance() {
        var token = peek();
        if (token.type() != TokenType.END) {
            current++;
        }
        return token;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private void checkDepth(int depth) {
        if (depth > maxDepth) {
            throw malformed(
                "Filter expression exceeds maximum depth " + maxDepth,
                null);
        }
    }

    private static QueryValidationException error(String message, Token token) {
        return malformed(message + " at position " + token.position(), null);
    }

    private static QueryValidationException malformed(String message, Throwable cause) {
        return new QueryValidationException(
            QueryErrorCode.MALFORMED_QUERY,
            message,
            "filter",
            cause);
    }

    private enum TokenType {
        WORD,
        STRING,
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        END
    }

    private record Token(TokenType type, String text, int position) {
    }

    private static final class Lexer {
        private final String source;
        private final List<Token> result = new ArrayList<>();
        private int current;

        private Lexer(String source) {
            this.source = source;
        }

        private List<Token> tokenize() {
            while (current < source.length()) {
                scan();
            }
            result.add(new Token(TokenType.END, "", current));
            return List.copyOf(result);
        }

        private void scan() {
            var start = current;
            var character = source.charAt(current++);
            if (Character.isWhitespace(character)) {
                return;
            }
            switch (character) {
                case '(' -> add(TokenType.LEFT_PAREN, "(", start);
                case ')' -> add(TokenType.RIGHT_PAREN, ")", start);
                case ',' -> add(TokenType.COMMA, ",", start);
                case '=' -> {
                    requireNext('=', "Expected '=='", start);
                    add(TokenType.EQ, "==", start);
                }
                case '!' -> {
                    requireNext('=', "Expected '!='", start);
                    add(TokenType.NE, "!=", start);
                }
                case '>' -> add(matchNext('=') ? TokenType.GTE : TokenType.GT,
                    source.substring(start, current), start);
                case '<' -> add(matchNext('=') ? TokenType.LTE : TokenType.LT,
                    source.substring(start, current), start);
                case '\'', '"' -> quoted(character, start);
                default -> word(start);
            }
        }

        private void word(int start) {
            while (current < source.length() && !delimiter(source.charAt(current))) {
                current++;
            }
            add(TokenType.WORD, source.substring(start, current), start);
        }

        private void quoted(char quote, int start) {
            var value = new StringBuilder();
            while (current < source.length()) {
                var character = source.charAt(current++);
                if (character == quote) {
                    add(TokenType.STRING, value.toString(), start);
                    return;
                }
                if (character == '\\') {
                    if (current >= source.length()) {
                        throw malformed("Unterminated escape at position " + start, null);
                    }
                    value.append(source.charAt(current++));
                } else {
                    value.append(character);
                }
            }
            throw malformed("Unterminated quoted value at position " + start, null);
        }

        private void requireNext(char expected, String message, int position) {
            if (!matchNext(expected)) {
                throw malformed(message + " at position " + position, null);
            }
        }

        private boolean matchNext(char expected) {
            if (current < source.length() && source.charAt(current) == expected) {
                current++;
                return true;
            }
            return false;
        }

        private void add(TokenType type, String text, int position) {
            result.add(new Token(type, text, position));
        }

        private static boolean delimiter(char character) {
            return Character.isWhitespace(character)
                || character == '('
                || character == ')'
                || character == ','
                || character == '='
                || character == '!'
                || character == '>'
                || character == '<'
                || character == '\''
                || character == '"';
        }
    }
}
