package io.github.emmanuelcazarez.criteriaforge.test;

import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import java.util.Arrays;
import org.assertj.core.api.AbstractAssert;

/** AssertJ assertions for testing CriteriaForge query policies. */
public final class QueryPolicyAssertions
        extends AbstractAssert<QueryPolicyAssertions, QueryPolicy> {

    private QueryPolicyAssertions(QueryPolicy actual) {
        super(actual, QueryPolicyAssertions.class);
    }

    public static QueryPolicyAssertions assertThat(QueryPolicy actual) {
        return new QueryPolicyAssertions(actual);
    }

    public QueryPolicyAssertions hasMaxPageSize(int expected) {
        isNotNull();
        if (actual.maxPageSize() != expected) {
            failWithMessage(
                "Expected maxPageSize to be <%s> but was <%s>",
                expected,
                actual.maxPageSize());
        }
        return this;
    }

    public QueryPolicyAssertions allowsRelationshipTraversal() {
        isNotNull();
        if (!actual.relationshipTraversal()) {
            failWithMessage("Expected relationshipTraversal to be <true> but was <false>");
        }
        return this;
    }

    public QueryPolicyAssertions allowsFields(String... fields) {
        isNotNull();
        var rejected = Arrays.stream(fields).filter(field -> !actual.isFieldAllowed(field)).toList();
        if (!rejected.isEmpty()) {
            failWithMessage("Expected fields to be allowed but these were rejected: <%s>", rejected);
        }
        return this;
    }

    public QueryPolicyAssertions deniesFields(String... fields) {
        isNotNull();
        var allowed = Arrays.stream(fields).filter(actual::isFieldAllowed).toList();
        if (!allowed.isEmpty()) {
            failWithMessage("Expected fields to be denied but these were allowed: <%s>", allowed);
        }
        return this;
    }
}
