package io.github.emmanuelcazarez.criteriaforge.test;

import static io.github.emmanuelcazarez.criteriaforge.test.QueryPolicyAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import org.junit.jupiter.api.Test;

class QueryPolicyAssertionsTest {

    @Test
    void describesConsumerPolicyExpectationsFluently() {
        var policy = QueryPolicy.builder()
            .maxPageSize(40)
            .relationshipTraversal(true)
            .allowFields("id", "customer.name")
            .denyFields("secret")
            .build();

        assertThat(policy)
            .hasMaxPageSize(40)
            .allowsRelationshipTraversal()
            .allowsFields("id", "customer.name")
            .deniesFields("secret");
    }

    @Test
    void failureMessagesNameThePolicyPropertyAndActualValue() {
        var policy = QueryPolicy.builder().maxPageSize(40).build();

        assertThatThrownBy(() -> assertThat(policy).hasMaxPageSize(10))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("maxPageSize")
            .hasMessageContaining("40");
    }
}
