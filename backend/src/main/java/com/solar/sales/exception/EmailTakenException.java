package com.solar.sales.exception;

public class EmailTakenException extends RuntimeException {
    public EmailTakenException() {
        super("An account with this email already exists. Sign in instead.");
    }
}
