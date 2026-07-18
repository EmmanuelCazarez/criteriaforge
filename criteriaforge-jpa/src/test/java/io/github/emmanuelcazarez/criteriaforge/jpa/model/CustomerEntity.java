package io.github.emmanuelcazarez.criteriaforge.jpa.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class CustomerEntity {
    @Id
    @GeneratedValue
    private Long customerKey;

    private String name;
    private String country;

    protected CustomerEntity() {
    }

    public CustomerEntity(String name, String country) {
        this.name = name;
        this.country = country;
    }

    public Long getCustomerKey() {
        return customerKey;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }
}
