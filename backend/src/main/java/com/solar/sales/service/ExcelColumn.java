package com.solar.sales.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Columns of the purchase upload sheet. Headers are matched case-insensitively ignoring spaces and
 * punctuation, and each column accepts a few common aliases, so column order does not matter.
 */
public enum ExcelColumn {
    INVOICE_NUMBER("Invoice No", true, 16, "invoice", "invoice number", "invoice id", "bill no", "bill number"),
    PURCHASE_DATE("Purchase Date", true, 14, "date", "sale date", "order date"),
    CUSTOMER_NAME("Customer Name", true, 24, "customer", "name", "client name"),
    PHONE("Phone", false, 15, "mobile", "phone number", "contact", "contact no"),
    EMAIL("Email", false, 26, "email id", "e-mail"),
    CITY("City", false, 14),
    STATE("State", false, 16),
    PANEL_TYPE("Panel Type", false, 18, "panel", "module type"),
    CAPACITY_KW("Capacity (kW)", true, 13, "capacity", "system capacity", "kw", "system size", "capacity kw"),
    PANEL_COUNT("Panel Count", false, 12, "panels", "no of panels", "number of panels", "qty"),
    INVERTER_BRAND("Inverter Brand", false, 16, "inverter"),
    TOTAL_AMOUNT("Total Amount", true, 15, "amount", "total", "price", "total price", "sale amount"),
    AMOUNT_PAID("Amount Paid", false, 14, "paid", "paid amount", "advance"),
    INSTALLATION_STATUS("Installation Status", false, 18, "installation", "install status"),
    INSTALLATION_DATE("Installation Date", false, 16, "install date", "installed on"),
    SALESPERSON("Salesperson", false, 18, "sales person", "sales executive", "agent");

    private final String header;
    private final boolean required;
    private final int width;
    private final String[] aliases;

    ExcelColumn(String header, boolean required, int width, String... aliases) {
        this.header = header;
        this.required = required;
        this.width = width;
        this.aliases = aliases;
    }

    public String header() { return header; }
    public boolean required() { return required; }
    public int width() { return width; }

    public static Optional<ExcelColumn> match(String rawHeader) {
        String key = normalize(rawHeader);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(c -> Stream.concat(Stream.of(c.header), Arrays.stream(c.aliases))
                        .map(ExcelColumn::normalize)
                        .anyMatch(key::equals))
                .findFirst();
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
