package io.github.emmanuelcazarez.criteriaforge.jpa.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class ProductEntity {
    @Id
    @GeneratedValue
    private Long productKey;

    private String name;

    protected ProductEntity() {
    }

    public ProductEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
