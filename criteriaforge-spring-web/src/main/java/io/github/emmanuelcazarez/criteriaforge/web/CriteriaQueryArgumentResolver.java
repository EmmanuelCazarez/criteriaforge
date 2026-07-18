package io.github.emmanuelcazarez.criteriaforge.web;

import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import io.github.emmanuelcazarez.criteriaforge.web.annotation.CriteriaQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CriteriaQuery}-annotated controller parameters. */
public final class CriteriaQueryArgumentResolver implements HandlerMethodArgumentResolver {
    private final QueryParameterParser parser;

    public CriteriaQueryArgumentResolver(QueryParameterParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == QuerySpec.class
            && parameter.hasParameterAnnotation(CriteriaQuery.class);
    }

    @Override
    public QuerySpec resolveArgument(
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
