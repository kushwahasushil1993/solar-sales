package com.solar.sales.dto;

import java.util.List;
import java.util.Set;

/**
 * One data row read from an uploaded sheet.
 *
 * @param errors       cell-level problems (unparseable number, bad date, ...)
 * @param failedFields SaleRequest property names whose cell could not be parsed, so bean validation
 *                     does not report a second "is required" error for the same cell
 */
public record ParsedRow(int rowNumber, SaleRequest sale, List<String> errors, Set<String> failedFields) {
}
