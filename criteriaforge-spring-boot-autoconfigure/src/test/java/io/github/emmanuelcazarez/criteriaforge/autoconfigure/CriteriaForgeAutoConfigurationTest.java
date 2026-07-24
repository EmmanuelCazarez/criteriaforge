package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicyRegistration;
import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryEngine;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyProvider;
import io.github.emmanuelcazarez.criteriaforge.web.CriteriaForgeWebMvcConfigurer;
import io.github.emmanuelcazarez.criteriaforge.web.QueryParameterParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.LinkedMultiValueMap;

class CriteriaForgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            CriteriaForgeAutoConfiguration.class,
            CriteriaForgeWebAutoConfiguration.class));

    @Test
    void configuresTheQueryEngineWithExplicitPerEntityPolicies() {
        contextRunner
            .withUserConfiguration(JpaBeans.class, RegisteredPolicy.class)
            .run(context -> {
            assertThat(context).hasSingleBean(QueryEngine.class);
            assertThat(context).hasSingleBean(QueryPolicyProvider.class);
            var policy = context.getBean(QueryPolicyProvider.class).policyFor(Object.class);
            assertThat(policy.maxPageSize()).isEqualTo(40);
            assertThat(policy.allowedFields()).contains("id");
        });
    }

    @Test
    void failsClosedWhenAnEntityHasNoRegisteredPolicy() {
        contextRunner
            .withUserConfiguration(JpaBeans.class)
            .run(context -> {
                var provider = context.getBean(QueryPolicyProvider.class);
                assertThatThrownBy(() -> provider.policyFor(Object.class))
                    .isInstanceOfSatisfying(QueryValidationException.class, error ->
                        assertThat(error.code())
                            .isEqualTo(QueryErrorCode.QUERY_POLICY_NOT_FOUND));
            });
    }

    @Test
    void rejectsDuplicateRegistrationsDuringStartup() {
        contextRunner
            .withUserConfiguration(JpaBeans.class, DuplicatePolicies.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("Duplicate CriteriaForge policy registration");
            });
    }

    @Test
    void backsOffForConsumerBeansAndWithoutJpa() {
        contextRunner
            .withUserConfiguration(JpaBeans.class, ConsumerQueryEngine.class)
            .run(context -> assertThat(context)
                .hasSingleBean(QueryEngine.class)
                .getBean(QueryEngine.class)
                .isSameAs(context.getBean("consumerQueryEngine")));

        contextRunner.run(context -> assertThat(context)
            .doesNotHaveBean(QueryEngine.class));
    }

    @Test
    void configuresTheOptionalWebAdapterWhenItsModuleIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QueryParameterParser.class);
            assertThat(context).hasSingleBean(CriteriaForgeWebMvcConfigurer.class);
        });
    }

    @Test
    void bindsReadableFilterParserLimits() {
        contextRunner
            .withPropertyValues(
                "criteriaforge.web.max-filter-length=10",
                "criteriaforge.web.max-expression-depth=2")
            .run(context -> {
                var parameters = new LinkedMultiValueMap<String, String>();
                parameters.add("filter", "status == PAID");

                assertThatThrownBy(() ->
                    context.getBean(QueryParameterParser.class).parse(parameters))
                    .isInstanceOf(QueryValidationException.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class JpaBeans {
        @Bean
        EntityManager entityManager() {
            var metamodel = proxy(Metamodel.class);
            return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[] {EntityManager.class},
                (instance, method, arguments) -> method.getName().equals("getMetamodel")
                    ? metamodel
                    : defaultValue(method.getReturnType()));
        }

        @Bean
        EntityManagerFactory entityManagerFactory() {
            return proxy(EntityManagerFactory.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerQueryEngine {
        @Bean
        QueryEngine consumerQueryEngine() {
            return new QueryEngine() {
                @Override
                public QueryResult<?> execute(Class<?> entityType, QueryRequest query) {
                    return new QueryResult<>(List.of(), 0, 0, 1);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RegisteredPolicy {
        @Bean
        QueryPolicyRegistration objectQueryPolicy() {
            return QueryPolicyRegistration.forEntity(
                Object.class,
                QueryPolicy.builder().allowFields("id").maxPageSize(40).build());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicatePolicies {
        @Bean
        QueryPolicyRegistration firstObjectPolicy() {
            return QueryPolicyRegistration.forEntity(Object.class, QueryPolicy.defaults());
        }

        @Bean
        QueryPolicyRegistration secondObjectPolicy() {
            return QueryPolicyRegistration.forEntity(Object.class, QueryPolicy.defaults());
        }
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (instance, method, arguments) -> defaultValue(method.getReturnType())));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
