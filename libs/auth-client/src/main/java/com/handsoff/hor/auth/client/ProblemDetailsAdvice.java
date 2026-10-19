package com.handsoff.hor.auth.client;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsAdvice {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException exception) {
        ErrorCode code = exception.errorCode();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), exception.getMessage());
        body.setTitle(org.springframework.http.HttpStatus.valueOf(code.status().value()).getReasonPhrase());
        body.setProperty("code", code.code());
        exception.properties().forEach(body::setProperty);
        return ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        body.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.setProperty("code", CommonErrorCode.VALIDATION_FAILED.code());
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> handleRequestParsing(Exception exception) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : "request";
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        body.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.setProperty("code", CommonErrorCode.VALIDATION_FAILED.code());
        body.setProperty("errors", List.of(Map.of("field", field, "message", "Invalid value.")));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred.");
        body.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        body.setProperty("code", "INTERNAL_ERROR");
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private Map<String, String> fieldError(FieldError fieldError) {
        return Map.of("field", fieldError.getField(), "message", fieldError.getDefaultMessage() == null
                ? "Invalid value." : fieldError.getDefaultMessage());
    }
}
