package io.github.emmanuelcazarez.criteriaforge.example;

import static io.github.emmanuelcazarez.criteriaforge.core.Filters.field;

import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.core.QueryResult;
import io.github.emmanuelcazarez.criteriaforge.example.domain.Order;
import io.github.emmanuelcazarez.criteriaforge.example.domain.OrderStatus;
import io.github.emmanuelcazarez.criteriaforge.jpa.QueryEngine;
import java.math.BigDecimal;
import java.util.Collection;
import org.springframework.stereotype.Service;

/** Demonstrates building a typed CriteriaForge query inside application code. */
@Service
public class OrderSearchService {
    private final QueryEngine queryEngine;

    OrderSearchService(QueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    public QueryResult<?> findPaidOrders(
            BigDecimal minimumTotal, Collection<String> countries) {
        var filter = field("status").eq(OrderStatus.PAID)
            .and(field("amount").gte(minimumTotal))
            .and(field("customer.country").in(countries));
        var query = QueryRequest.builder()
            .select("reference")
            .selectAs("buyerName", "buyer.name")
            .selectAs("amount", "orderTotal")
            .where(filter)
            .orderByDescending("amount")
            .limit(20)
            .build();

        return queryEngine.execute(Order.class, query);
    }
}
