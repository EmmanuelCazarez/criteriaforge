package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.jpa.CriteriaForgeExecutor;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Metamodel;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CriteriaForgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CriteriaForgeAutoConfiguration.class));

    @Test
    void configuresTheExecutorAndDefaultPolicyWhenJpaIsAvailable() {
        contextRunner.withUserConfiguration(JpaBeans.class).run(context -> {
            assertThat(context).hasSingleBean(CriteriaForgeExecutor.class);
            assertThat(context).hasSingleBean(QueryPolicyResolver.class);
            var policy = context.getBean(QueryPolicyResolver.class).resolve(Object.class);
            assertThat(policy.maxPageSize()).isEqualTo(100);
            assertThat(policy.maxConditions()).isEqualTo(25);
            assertThat(policy.maxDepth()).isEqualTo(2);
            assertThat(policy.relationshipTraversal()).isFalse();
        });
    }

    @Test
    void bindsConsumerSafetyProperties() {
        contextRunner
            .withUserConfiguration(JpaBeans.class)
            .withPropertyValues(
                "criteriaforge.query.max-page-size=40",
                "criteriaforge.query.max-conditions=12",
                "criteriaforge.query.max-depth=1",
                "criteriaforge.query.relationship-traversal=true")
            .run(context -> {
                var policy = context.getBean(QueryPolicyResolver.class).resolve(Object.class);
                assertThat(policy.maxPageSize()).isEqualTo(40);
                assertThat(policy.maxConditions()).isEqualTo(12);
                assertThat(policy.maxDepth()).isEqualTo(1);
                assertThat(policy.relationshipTraversal()).isTrue();
            });
    }

    @Test
    void backsOffForConsumerBeansAndWithoutJpa() {
        contextRunner
            .withUserConfiguration(JpaBeans.class, ConsumerExecutor.class)
            .run(context -> assertThat(context)
                .hasSingleBean(CriteriaForgeExecutor.class)
                .getBean(CriteriaForgeExecutor.class)
                .isSameAs(context.getBean("consumerCriteriaForgeExecutor")));

        contextRunner.run(context -> assertThat(context)
            .doesNotHaveBean(CriteriaForgeExecutor.class));
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
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerExecutor {
        @Bean
        CriteriaForgeExecutor consumerCriteriaForgeExecutor() {
            return new CriteriaForgeExecutor() {
                @Override
                public <T> QueryResult<T> findAll(Class<T> entityType, QuerySpec query) {
                    return new QueryResult<>(List.of(), 0, 0, 1);
                }

                @Override
                public QueryResult<Map<String, Object>> findProjected(
                        Class<?> entityType, QuerySpec query) {
                    return new QueryResult<>(List.of(), 0, 0, 1);
                }
            };
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
