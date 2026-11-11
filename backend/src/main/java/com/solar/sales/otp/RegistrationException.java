package com.solar.sales.otp;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

/** Phone registration could not be completed; {@code field} names the form field to highlight. */
public class RegistrationException extends AuthenticationException {

    private final HttpStatus status;
    private final String field;

    public RegistrationException(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public HttpStatus status() {
        return status;
    }

    public String field() {
        return field;
    }
}
