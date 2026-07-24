package io.github.emmanuelcazarez.criteriaforge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class QueryPolicyRegistrationTest {

    @Test
    void bindsOneEntityTypeToOnePolicy() {
        var policy = QueryPolicy.builder().allowFields("id").build();

        var registration = QueryPolicyRegistration.forEntity(SampleEntity.class, policy);

        assertThat(registration.entityType()).isEqualTo(SampleEntity.class);
        assertThat(registration.policy()).isSameAs(policy);
    }

    @Test
    void rejectsMissingEntityOrPolicy() {
        assertThatNullPointerException().isThrownBy(
            () -> QueryPolicyRegistration.forEntity(null, QueryPolicy.defaults()));
        assertThatNullPointerException().isThrownBy(
            () -> QueryPolicyRegistration.forEntity(SampleEntity.class, null));
    }

    private static final class SampleEntity {
    }
}
