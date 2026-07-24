package io.github.emmanuelcazarez.criteriaforge.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import java.lang.reflect.Modifier;
import java.util.Arrays;
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
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.core.Sorting")).isPresent();
        assertThat(load("io.github.emmanuelcazarez.criteriaforge.core.Pagination")).isPresent();
        assertThat(load(coreType("Sort", "Spec"))).isEmpty();
        assertThat(load(coreType("Page", "Spec"))).isEmpty();

        var builderType = load(
            "io.github.emmanuelcazarez.criteriaforge.core.QueryRequest$Builder").orElseThrow();
        assertThat(builderType.getMethod("orderByAscending", String.class)).isNotNull();
        assertThat(builderType.getMethod("orderByDescending", String.class)).isNotNull();
        assertThat(builderType.getMethod("offset", int.class)).isNotNull();
        assertThat(builderType.getMethod("limit", int.class)).isNotNull();
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

    @Test
    void exposesFiltersWithoutConcreteExpressionImplementations() throws Exception {
        var expressionType = load(
            "io.github.emmanuelcazarez.criteriaforge.core.FilterExpression").orElseThrow();
        var filtersType = load("io.github.emmanuelcazarez.criteriaforge.core.Filters").orElseThrow();
        var fieldType = load(
            "io.github.emmanuelcazarez.criteriaforge.core.FilterField").orElseThrow();

        assertThat(List.of("Condition", "FilterGroup", "Negation"))
            .allSatisfy(typeName -> assertThat(load(
                    "io.github.emmanuelcazarez.criteriaforge.core." + typeName))
                .hasValueSatisfying(type -> assertThat(Modifier.isPublic(type.getModifiers()))
                    .isFalse()));
        assertThat(filtersType.getMethod("field", String.class).getReturnType())
            .isEqualTo(fieldType);
        assertThat(Arrays.stream(filtersType.getMethods())
                .filter(method -> method.getDeclaringClass().equals(filtersType))
                .filter(method -> !method.getName().equals("field")))
            .allSatisfy(method -> assertThat(method.getReturnType()).isEqualTo(expressionType));
        assertThat(Arrays.stream(fieldType.getMethods())
                .filter(method -> method.getDeclaringClass().equals(fieldType)))
            .allSatisfy(method -> assertThat(method.getReturnType()).isEqualTo(expressionType));
    }

    private static Optional<Class<?>> load(String typeName) {
        try {
            return Optional.of(Class.forName(typeName));
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        }
    }

    private static String coreType(String... nameParts) {
        return "io.github.emmanuelcazarez.criteriaforge.core." + String.join("", nameParts);
    }
}
