package io.github.emmanuelcazarez.criteriaforge.web;

import io.github.emmanuelcazarez.criteriaforge.core.QueryRequest;
import org.springframework.util.MultiValueMap;

/** Converts HTTP query parameters into a transport-neutral query request. */
@FunctionalInterface
public interface QueryParameterParser {

    QueryRequest parse(MultiValueMap<String, String> parameters);
}
