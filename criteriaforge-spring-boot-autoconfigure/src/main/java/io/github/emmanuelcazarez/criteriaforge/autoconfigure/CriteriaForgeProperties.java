package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import io.github.emmanuelcazarez.criteriaforge.core.QueryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Global default safety properties for CriteriaForge queries. */
@ConfigurationProperties("criteriaforge.query")
public class CriteriaForgeProperties {
    private int maxPageSize = QueryPolicy.DEFAULT_MAX_PAGE_SIZE;
    private int maxConditions = QueryPolicy.DEFAULT_MAX_CONDITIONS;
    private int maxDepth = QueryPolicy.DEFAULT_MAX_DEPTH;
    private boolean relationshipTraversal;

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxConditions() {
        return maxConditions;
    }

    public void setMaxConditions(int maxConditions) {
        this.maxConditions = maxConditions;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public boolean isRelationshipTraversal() {
        return relationshipTraversal;
    }

    public void setRelationshipTraversal(boolean relationshipTraversal) {
        this.relationshipTraversal = relationshipTraversal;
    }

    QueryPolicy toPolicy() {
        return QueryPolicy.builder()
            .maxPageSize(maxPageSize)
            .maxConditions(maxConditions)
            .maxDepth(maxDepth)
            .relationshipTraversal(relationshipTraversal)
            .build();
    }
}
