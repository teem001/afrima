package com.afrismart.shared.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(
                ex.getCode(),
                ex.getMessage(),
                ex.getStatus().value(),
                Instant.now(),
                List.of()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCodes.VALIDATION_FAILED,
                "Request validation failed",
                400,
                Instant.now(),
                details
        ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadInput(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCodes.VALIDATION_FAILED,
                "Malformed request",
                400,
                Instant.now(),
                List.of(ex.getMessage() == null ? "" : ex.getMessage())
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(new ErrorResponse(
                "FORBIDDEN",
                ex.getMessage() == null ? "Access denied" : ex.getMessage(),
                403,
                Instant.now(),
                List.of()
        ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(401).body(new ErrorResponse(
                ErrorCodes.INVALID_CREDENTIALS,
                "Invalid email or password",
                401,
                Instant.now(),
                List.of()
        ));
    }
}
