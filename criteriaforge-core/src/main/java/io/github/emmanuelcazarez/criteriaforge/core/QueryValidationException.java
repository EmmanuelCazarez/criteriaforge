package io.github.emmanuelcazarez.criteriaforge.core;

/** Indicates that a query was rejected before persistence execution. */
public final class QueryValidationException extends CriteriaForgeException {

    public QueryValidationException(QueryErrorCode code, String message) {
        super(code, message);
    }

    public QueryValidationException(QueryErrorCode code, String message, String path) {
        super(code, message, path);
    }

    public QueryValidationException(
            QueryErrorCode code, String message, String path, Throwable cause) {
        super(code, message, path, cause);
    }
}
