package com.handsoff.hor.auth.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ProblemDetailsAdviceTest {
    private final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

    @Test
    void mapsDomainExceptionToProblemDetails() {
        var response = advice.handleDomainException(
                new DomainException(CommonErrorCode.NOT_FOUND, "Store not found."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    void includesFieldErrorsForInvalidRequests() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "Invalid email."));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, binding);

        var response = advice.handleValidation(exception);
        ProblemDetail body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(body.getProperties().get("errors").toString()).contains("email", "Invalid email.");
    }
}
