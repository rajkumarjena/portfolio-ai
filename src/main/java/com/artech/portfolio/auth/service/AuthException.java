package com.artech.portfolio.auth.service;

import org.springframework.http.HttpStatus;

/**
 * Auth business rule violation. Carries the HTTP status so the controllers can
 * return a clean JSON {message: ...} body instead of Spring Boot's generic error page.
 */
public class AuthException extends RuntimeException {
    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
