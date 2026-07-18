package io.github.emmanuelcazarez.criteriaforge.example;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.example.domain.Order;
import io.github.emmanuelcazarez.criteriaforge.jpa.CriteriaForgeExecutor;
import io.github.emmanuelcazarez.criteriaforge.web.annotation.CriteriaQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderQueryController {
    private final CriteriaForgeExecutor executor;

    OrderQueryController(CriteriaForgeExecutor executor) {
        this.executor = executor;
    }

    @GetMapping
    QueryResult<?> findAll(@CriteriaQuery QuerySpec query) {
        return query.fields().isEmpty()
            ? executor.findAll(Order.class, query)
            : executor.findProjected(Order.class, query);
    }
}
