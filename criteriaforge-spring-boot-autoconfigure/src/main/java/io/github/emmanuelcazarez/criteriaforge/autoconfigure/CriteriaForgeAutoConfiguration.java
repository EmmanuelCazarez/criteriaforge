package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import io.github.emmanuelcazarez.criteriaforge.core.QueryErrorCode;
import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicyRegistration;
import io.github.emmanuelcazarez.criteriaforge.core.QueryValidationException;
import io.github.emmanuelcazarez.criteriaforge.jpa.JpaQueryEngine;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryEngine;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import java.util.LinkedHashMap;
import java.util.List;

/** Creates CriteriaForge infrastructure when Jakarta Persistence is available. */
@AutoConfiguration
@AutoConfigureAfter(name = {
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
@ConditionalOnClass({EntityManager.class, QueryEngine.class})
public class CriteriaForgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    QueryPolicyProvider criteriaForgeQueryPolicyProvider(
            List<QueryPolicyRegistration> registrations) {
        var policies = new LinkedHashMap<Class<?>, QueryPolicyRegistration>();
        for (var registration : registrations) {
            var duplicate = policies.putIfAbsent(registration.entityType(), registration);
            if (duplicate != null) {
                throw new IllegalStateException(
                    "Duplicate CriteriaForge policy registration for "
                        + registration.entityType().getName());
            }
        }
        var immutablePolicies = java.util.Map.copyOf(policies);
        return entityType -> {
            var registration = immutablePolicies.get(entityType);
            if (registration == null) {
                throw new QueryValidationException(
                    QueryErrorCode.QUERY_POLICY_NOT_FOUND,
                    "No CriteriaForge query policy is registered for "
                        + entityType.getName(),
                    entityType.getName());
            }
            return registration.policy();
        };
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnMissingBean
    QueryEngine criteriaForgeQueryEngine(
            EntityManager entityManager, QueryPolicyProvider policyProvider) {
        return new JpaQueryEngine(entityManager, policyProvider);
    }
}
