package io.github.emmanuelcazarez.criteriaforge.jpa;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import java.util.Objects;

/** Executes validated dynamic queries against JPA-managed entity types. */
public interface QueryEngine {

    QueryResult<?> execute(Class<?> entityType, QueryRequest query);

    /** Executes an entity query with a statically typed result. */
    default <T> QueryResult<T> executeEntities(Class<T> entityType, QueryRequest query) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (!query.fields().isEmpty()) {
            throw new QueryValidationException(
                QueryErrorCode.UNSUPPORTED_PROJECTION,
                "Typed entity execution does not accept projection fields");
        }
        return execute(entityType, query).map(entityType::cast);
    }
}
