package com.solar.sales.exception;

/** A request that is authorized in general but not allowed in this case (e.g. an admin disabling themself). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
