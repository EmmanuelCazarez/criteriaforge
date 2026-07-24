package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import io.github.emmanuelcazarez.criteriaforge.web.DefaultQueryParameterParser;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Safety limits for the optional readable HTTP filter grammar. */
@ConfigurationProperties("criteriaforge.web")
public class CriteriaForgeWebProperties {
    private int maxFilterLength = DefaultQueryParameterParser.DEFAULT_MAX_FILTER_LENGTH;
    private int maxExpressionDepth = DefaultQueryParameterParser.DEFAULT_MAX_EXPRESSION_DEPTH;

    public int getMaxFilterLength() {
        return maxFilterLength;
    }

    public void setMaxFilterLength(int maxFilterLength) {
        this.maxFilterLength = maxFilterLength;
    }

    public int getMaxExpressionDepth() {
        return maxExpressionDepth;
    }

    public void setMaxExpressionDepth(int maxExpressionDepth) {
        this.maxExpressionDepth = maxExpressionDepth;
    }
}
