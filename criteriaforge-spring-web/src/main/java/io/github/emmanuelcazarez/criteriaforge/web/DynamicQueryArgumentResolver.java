package io.github.emmanuelcazarez.criteriaforge.web;

import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import io.github.emmanuelcazarez.criteriaforge.web.annotation.DynamicQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link DynamicQuery}-annotated controller parameters. */
public final class DynamicQueryArgumentResolver implements HandlerMethodArgumentResolver {
    private final QueryParameterParser parser;

    public DynamicQueryArgumentResolver(QueryParameterParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == QueryRequest.class
            && parameter.hasParameterAnnotation(DynamicQuery.class);
    }

    @Override
    public QueryRequest resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer modelAndViewContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        var request = Objects.requireNonNull(
            webRequest.getNativeRequest(HttpServletRequest.class),
            "HTTP request must be available");
        var parameters = new LinkedMultiValueMap<String, String>();
        request.getParameterMap().forEach((name, values) ->
            parameters.addAll(name, Arrays.asList(values)));
        return parser.parse(parameters);
    }
}
