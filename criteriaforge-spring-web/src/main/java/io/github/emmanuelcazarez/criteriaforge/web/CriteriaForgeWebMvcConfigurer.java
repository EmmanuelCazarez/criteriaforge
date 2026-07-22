package io.github.emmanuelcazarez.criteriaforge.web;

import java.util.List;
import java.util.Objects;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers CriteriaForge's optional MVC query argument resolver. */
public final class CriteriaForgeWebMvcConfigurer implements WebMvcConfigurer {
    private final DynamicQueryArgumentResolver resolver;

    public CriteriaForgeWebMvcConfigurer(QueryParameterParser parser) {
        resolver = new DynamicQueryArgumentResolver(
            Objects.requireNonNull(parser, "parser must not be null"));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
