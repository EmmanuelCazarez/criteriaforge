package io.github.emmanuelcazarez.criteriaforge.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryEngineTest {

    @Test
    void defaultTypedExecutionKeepsExistingImplementationsCompatible() {
        QueryEngine engine = (entityType, query) ->
            new QueryResult<>(List.of("first", "second"), 4, 2, 2);

        var result = engine.executeEntities(
            String.class,
            QueryRequest.builder().offset(2).limit(2).build());

        assertThat(result.content()).containsExactly("first", "second");
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.offset()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(2);
    }

    @Test
    void defaultTypedExecutionRejectsProjectionRequests() {
        QueryEngine engine = (entityType, query) ->
            new QueryResult<>(List.of(), 0, 0, 1);

        assertThatThrownBy(() -> engine.executeEntities(
            String.class,
            QueryRequest.builder().select("value").build()))
            .isInstanceOfSatisfying(QueryValidationException.class, error ->
                assertThat(error.code()).isEqualTo(QueryErrorCode.UNSUPPORTED_PROJECTION));
    }
}
