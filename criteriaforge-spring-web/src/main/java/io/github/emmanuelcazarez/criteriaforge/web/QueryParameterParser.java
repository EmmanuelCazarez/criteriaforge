package io.github.emmanuelcazarez.criteriaforge.web;

import io.github.emmanuelcazarez.criteriaforge.core.QuerySpec;
import org.springframework.util.MultiValueMap;

/** Converts HTTP query parameters into a transport-neutral query specification. */
@FunctionalInterface
public interface QueryParameterParser {

    QuerySpec parse(MultiValueMap<String, String> parameters);
}
