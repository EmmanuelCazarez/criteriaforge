package io.github.emmanuelcazarez.criteriaforge.example.domain;

import io.github.emmanuelcazarez.criteriaforge.core.annotation.QueryHidden;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue
    private Long id;

    private String reference;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal total;

    @ManyToOne(optional = false)
    private Customer customer;

    @QueryHidden
    private String internalNote;

    protected Order() {
    }

    public Order(
            String reference,
            OrderStatus status,
            BigDecimal total,
            Customer customer,
            String internalNote) {
        this.reference = reference;
        this.status = status;
        this.total = total;
        this.customer = customer;
        this.internalNote = internalNote;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Customer getCustomer() {
        return customer;
    }
}
