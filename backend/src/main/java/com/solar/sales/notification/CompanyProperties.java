package com.solar.sales.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Seller details printed on invoices and customer emails (app.company.*). */
@ConfigurationProperties("app.company")
public record CompanyProperties(String name, String email, String phone, String address, String gstin,
                                String website) {

    public CompanyProperties {
        name = name == null || name.isBlank() ? "SolarSales" : name.trim();
        email = blankToNull(email);
        phone = blankToNull(phone);
        address = blankToNull(address);
        gstin = blankToNull(gstin);
        website = blankToNull(website);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
