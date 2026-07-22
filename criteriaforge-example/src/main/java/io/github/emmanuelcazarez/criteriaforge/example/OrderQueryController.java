package io.github.emmanuelcazarez.criteriaforge.example;

import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.example.domain.Order;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryEngine;
import io.github.emmanuelcazarez.criteriaforge.web.annotation.DynamicQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {
    private final QueryEngine queryEngine;

    OrderQueryController(QueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    @GetMapping
    QueryResult<?> findAll(@DynamicQuery QueryRequest query) {
        return queryEngine.execute(Order.class, query);
    }
}
