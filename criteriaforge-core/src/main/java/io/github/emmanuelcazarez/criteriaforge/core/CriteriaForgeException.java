package io.github.emmanuelcazarez.criteriaforge.core;

import java.util.Objects;
import java.util.Optional;

/** Base exception for failures with stable public error semantics. */
public class CriteriaForgeException extends RuntimeException {
    private final QueryErrorCode code;
    private final String path;

    public CriteriaForgeException(QueryErrorCode code, String message) {
        this(code, message, null, null);
    }

    public CriteriaForgeException(QueryErrorCode code, String message, String path) {
        this(code, message, path, null);
    }

    public CriteriaForgeException(
            QueryErrorCode code, String message, String path, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.path = path;
    }

    public QueryErrorCode code() {
        return code;
    }

    public Optional<String> path() {
        return Optional.ofNullable(path);
    }
}
