package io.github.emmanuelcazarez.criteriaforge.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConsumerApiTest {

    @Test
    void exposesOneIntentionRevealingQueryContract() throws Exception {
        var requestType = load("io.github.emmanuelcazarez.criteriaforge.core.QueryRequest");
        var engineType = load("io.github.emmanuelcazarez.criteriaforge.jpa.QueryEngine");

        assertThat(requestType).isPresent();
        assertThat(engineType).isPresent();
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyProvider"))
            .isPresent();
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.web.annotation.DynamicQuery"))
            .isPresent();

        var execute = engineType.orElseThrow().getMethod(
            "execute", Class.class, requestType.orElseThrow());
        assertThat(execute.getReturnType()).isEqualTo(QueryResult.class);

        assertThat(load("io.github.emmanuelcazarez.criteriaforge.core.QuerySpec")).isEmpty();
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.jpa.CriteriaForgeExecutor"))
            .isEmpty();
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyResolver"))
            .isEmpty();
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.web.annotation.CriteriaQuery"))
            .isEmpty();
    }

    @Test
    void keepsJpaConstructionDetailsOutOfThePublicApi() {
        var implementationTypes = List.of(
            "JoinRegistry",
            "JpaPathResolver",
            "JpaPredicateBuilder",
            "JpaResolvedPath",
            "JpaValueConverter",
            "NestedMapAssembler");

        assertThat(implementationTypes)
            .allSatisfy(typeName -> assertThat(load(
                    "io.github.emmanuelcazarez.criteriaforge.jpa." + typeName))
                .hasValueSatisfying(type -> assertThat(Modifier.isPublic(type.getModifiers()))
                    .isFalse()));
    }

    private static Optional<Class<?>> load(String typeName) {
        try {
            return Optional.of(Class.forName(typeName));
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        }
    }
}
