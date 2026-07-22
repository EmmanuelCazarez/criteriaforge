package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Creates CriteriaForge infrastructure when Jakarta Persistence is available. */
@AutoConfiguration
@AutoConfigureAfter(name = {
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
@ConditionalOnClass({EntityManager.class, QueryEngine.class})
@EnableConfigurationProperties(CriteriaForgeProperties.class)
public class CriteriaForgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    QueryPolicyProvider criteriaForgeQueryPolicyProvider(CriteriaForgeProperties properties) {
        var policy = properties.toPolicy();
        return ignored -> policy;
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnMissingBean
    QueryEngine criteriaForgeQueryEngine(
            EntityManager entityManager, QueryPolicyProvider policyProvider) {
        return new JpaQueryEngine(entityManager, policyProvider);
    }
}
