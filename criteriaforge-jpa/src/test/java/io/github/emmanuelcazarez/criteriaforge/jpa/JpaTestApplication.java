package io.github.emmanuelcazarez.criteriaforge.jpa;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
class JpaTestApplication {

    @Bean
    DataSource dataSource(Environment environment) {
        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(environment.getProperty(
            "spring.datasource.driver-class-name", "org.h2.Driver"));
        dataSource.setUrl(environment.getProperty(
            "spring.datasource.url",
            "jdbc:h2:mem:criteriaforge;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        dataSource.setUsername(environment.getProperty("spring.datasource.username", "sa"));
        dataSource.setPassword(environment.getProperty("spring.datasource.password", ""));
        return dataSource;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("io.github.emmanuelcazarez.criteriaforge.jpa.model");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.show_sql", "false",
            "hibernate.generate_statistics", "true",
            "hibernate.jdbc.time_zone", "UTC"));
        return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    jakarta.persistence.EntityManager entityManager(EntityManagerFactory entityManagerFactory) {
        return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }
}
