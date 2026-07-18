package io.github.emmanuelcazarez.criteriaforge.jpa.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class OrderItemEntity {
    @Id
    @GeneratedValue
    private Long itemKey;

    @ManyToOne(optional = false)
    private ProductEntity product;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(ProductEntity product) {
        this.product = product;
    }

    public ProductEntity getProduct() {
        return product;
    }
}
