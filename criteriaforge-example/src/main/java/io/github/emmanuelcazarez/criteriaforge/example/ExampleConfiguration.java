package io.github.emmanuelcazarez.criteriaforge.example;

import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import io.github.emmanuelcazarez.criteriaforge.example.domain.Customer;
import io.github.emmanuelcazarez.criteriaforge.example.domain.CustomerRepository;
import io.github.emmanuelcazarez.criteriaforge.example.domain.Order;
import io.github.emmanuelcazarez.criteriaforge.example.domain.OrderRepository;
import io.github.emmanuelcazarez.criteriaforge.example.domain.OrderStatus;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryPolicyProvider;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {

    @Bean
    QueryPolicyProvider exampleQueryPolicy() {
        var policy = QueryPolicy.builder()
            .relationshipTraversal(true)
            .maxPageSize(100)
            .build();
        return ignored -> policy;
    }

    @Bean
    CommandLineRunner seedExampleData(
            CustomerRepository customers, OrderRepository orders) {
        return ignored -> {
            var ana = customers.save(new Customer("Ana", "MX"));
            var luis = customers.save(new Customer("Luis", "US"));
            orders.save(new Order(
                "ORD-100", OrderStatus.PAID, new BigDecimal("125.00"), ana, "private"));
            orders.save(new Order(
                "ORD-101", OrderStatus.CREATED, new BigDecimal("80.00"), ana, "private"));
            orders.save(new Order(
                "ORD-102", OrderStatus.PAID, new BigDecimal("250.00"), luis, "private"));
            orders.save(new Order(
                "ORD-103", OrderStatus.CANCELLED, new BigDecimal("20.00"), luis, "private"));
        };
    }
}
