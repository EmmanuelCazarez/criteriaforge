package io.github.emmanuelcazarez.criteriaforge.autoconfigure;

import io.github.emmanuelcazarez.criteriaforge.web.CriteriaForgeWebMvcConfigurer;
import io.github.emmanuelcazarez.criteriaforge.web.DefaultQueryParameterParser;
import io.github.emmanuelcazarez.criteriaforge.web.QueryParameterParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Activates HTTP query binding only when the optional Web integration is present. */
@AutoConfiguration(after = CriteriaForgeAutoConfiguration.class)
@ConditionalOnClass({QueryParameterParser.class, WebMvcConfigurer.class})
@EnableConfigurationProperties(CriteriaForgeWebProperties.class)
public class CriteriaForgeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    QueryParameterParser criteriaForgeQueryParameterParser(
            CriteriaForgeWebProperties properties) {
        return new DefaultQueryParameterParser(
            properties.getMaxFilterLength(),
            properties.getMaxExpressionDepth());
    }

    @Bean
    @ConditionalOnMissingBean(CriteriaForgeWebMvcConfigurer.class)
    CriteriaForgeWebMvcConfigurer criteriaForgeWebMvcConfigurer(QueryParameterParser parser) {
        return new CriteriaForgeWebMvcConfigurer(parser);
    }
}
