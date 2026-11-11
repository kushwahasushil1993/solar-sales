package com.solar.sales.exception;

/** The request clashes with the current state, e.g. a task someone else just picked up. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
