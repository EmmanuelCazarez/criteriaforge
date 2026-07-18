package io.github.emmanuelcazarez.criteriaforge.jpa.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class OrderEntity {
    @Id
    @GeneratedValue
    private Long orderKey;

    private String reference;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal total;

    @ManyToOne
    private CustomerEntity customer;

    @OneToMany
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    public OrderEntity(
            String reference, OrderStatus status, BigDecimal total, CustomerEntity customer) {
        this.reference = reference;
        this.status = status;
        this.total = total;
        this.customer = customer;
    }

    public Long getOrderKey() {
        return orderKey;
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

    public CustomerEntity getCustomer() {
        return customer;
    }

    public List<OrderItemEntity> getItems() {
        return items;
    }
}
