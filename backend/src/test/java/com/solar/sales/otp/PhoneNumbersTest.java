package com.solar.sales.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "9876543210, +919876543210",
            "98765 43210, +919876543210",
            "098765-43210, +919876543210",
            "+91 98765 43210, +919876543210",
            "0091 9876543210, +919876543210",
            "919876543210, +919876543210",
            "(+91) 98765.43210, +919876543210",
            "+1 415 555 2671, +14155552671",
            "+44 7911 123456, +447911123456"
    })
    void normalisesToE164(String raw, String expected) {
        assertThat(PhoneNumbers.normalize(raw, "91")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "12345", "abc9876543210", "+91 12345 67890", "+91 98765 4321", "+0123456789",
            "+1234567890123456"})
    void rejectsImplausibleNumbers(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(raw, "91")).isInstanceOf(InvalidPhoneException.class);
    }

    @Test
    void masksAllButLastFourDigits() {
        assertThat(PhoneNumbers.mask("+919876543210")).isEqualTo("+91 ••••••3210");
    }
}
