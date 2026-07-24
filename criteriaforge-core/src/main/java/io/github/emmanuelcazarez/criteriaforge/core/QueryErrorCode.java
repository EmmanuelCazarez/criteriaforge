package io.github.emmanuelcazarez.criteriaforge.core;

/** Stable machine-readable error codes exposed by CriteriaForge. */
public enum QueryErrorCode {
    MALFORMED_QUERY,
    UNKNOWN_FIELD,
    RELATIONSHIP_TRAVERSAL_DISABLED,
    UNSUPPORTED_OPERATOR,
    INCOMPATIBLE_OPERATOR,
    VALUE_CONVERSION_FAILED,
    FIELD_NOT_ALLOWED,
    PAGE_SIZE_EXCEEDED,
    CONDITION_LIMIT_EXCEEDED,
    RELATIONSHIP_DEPTH_EXCEEDED,
    UNSUPPORTED_PROJECTION,
    QUERY_POLICY_NOT_FOUND
}
