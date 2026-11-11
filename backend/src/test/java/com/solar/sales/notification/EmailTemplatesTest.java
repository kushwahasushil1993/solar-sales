package com.solar.sales.notification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    @ParameterizedTest
    @CsvSource({
            "0, ₹0",
            "999, ₹999",
            "1000, '₹1,000'",
            "100000, '₹1,00,000'",
            "325000, '₹3,25,000'",
            "28277888, '₹2,82,77,888'",
            "1234567.5, '₹12,34,567.50'",
            "-45000, '-₹45,000'"
    })
    void formatsRupeesWithIndianGrouping(String amount, String expected) {
        assertThat(EmailTemplates.money(new BigDecimal(amount))).isEqualTo(expected);
    }
}
