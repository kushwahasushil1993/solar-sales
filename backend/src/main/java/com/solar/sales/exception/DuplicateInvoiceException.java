package com.solar.sales.exception;

public class DuplicateInvoiceException extends RuntimeException {
    public DuplicateInvoiceException(String invoiceNumber) {
        super("Invoice number '" + invoiceNumber + "' already exists");
    }
}
