package io.github.emmanuelcazarez.criteriaforge.example;

import io.github.emmanuelcazarez.criteriaforge.core.CriteriaForgeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class CriteriaForgeErrorHandler {

    @ExceptionHandler(CriteriaForgeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handle(CriteriaForgeException exception) {
        return new ErrorResponse(
            exception.code().name(),
            exception.getMessage(),
            exception.path().orElse(null));
    }

    record ErrorResponse(String code, String message, String path) {
    }
}
