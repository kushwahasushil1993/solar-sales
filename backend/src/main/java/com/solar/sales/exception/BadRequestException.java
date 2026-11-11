package com.solar.sales.exception;

/** A request that is well-formed but not acceptable; {@code field} names the input to highlight. */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
