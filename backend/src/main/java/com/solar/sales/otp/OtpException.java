package com.solar.sales.otp;

import org.springframework.http.HttpStatus;

/** A refused OTP request (unknown number, already registered, too many requests, SMS failure, ...). */
public class OtpException extends RuntimeException {

    private final HttpStatus status;

    public OtpException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
