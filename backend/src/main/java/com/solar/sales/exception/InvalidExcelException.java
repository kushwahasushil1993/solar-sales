package com.solar.sales.exception;

/** The uploaded file as a whole cannot be processed (wrong type, missing headers, unreadable). */
public class InvalidExcelException extends RuntimeException {
    public InvalidExcelException(String message) {
        super(message);
    }

    public InvalidExcelException(String message, Throwable cause) {
        super(message, cause);
    }
}
