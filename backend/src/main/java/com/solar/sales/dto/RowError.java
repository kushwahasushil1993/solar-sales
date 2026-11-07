package com.solar.sales.dto;

/** A problem found on one Excel row. {@code row} is the 1-based row number as shown in Excel. */
public record RowError(int row, String invoiceNumber, String message) {
}
