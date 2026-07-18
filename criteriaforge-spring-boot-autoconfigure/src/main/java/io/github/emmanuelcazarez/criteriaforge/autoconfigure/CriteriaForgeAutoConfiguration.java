package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import io.github.emmanuelcazarez.criteriaforge.jpa.CriteriaForgeExecutor;
import io.github.emmanuelcazarez.criteriaforge.jpa.DefaultCriteriaForgeExecutor;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyResolver;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Creates CriteriaForge infrastructure when Jakarta Persistence is available. */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class, CriteriaForgeExecutor.class})
@EnableConfigurationProperties(CriteriaForgeProperties.class)
public class CriteriaForgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    QueryPolicyResolver criteriaForgeQueryPolicyResolver(CriteriaForgeProperties properties) {
        var policy = properties.toPolicy();
        return ignored -> policy;
    }

    @Bean
    @ConditionalOnBean(EntityManager.class)
    @ConditionalOnMissingBean
    CriteriaForgeExecutor criteriaForgeExecutor(
            EntityManager entityManager, QueryPolicyResolver policyResolver) {
        return new DefaultCriteriaForgeExecutor(entityManager, policyResolver);
    }
}
