package com.solar.sales.otp;

/** Normalises user-typed mobile numbers to E.164 (+919876543210). */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Accepts "98765 43210", "098765-43210", "+91 98765 43210", "0091 9876543210" or "919876543210".
     * Numbers without a country code get {@code defaultCountryCode}.
     *
     * @throws InvalidPhoneException if it is not a plausible mobile number
     */
    public static String normalize(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPhoneException();
        }
        String s = raw.trim().replaceAll("[\\s().\\-]", "");
        if (s.startsWith("00")) {
            s = "+" + s.substring(2);
        }
        String digits;
        if (s.startsWith("+")) {
            digits = s.substring(1);
        } else {
            if (s.startsWith("0")) {
                s = s.substring(1); // national trunk prefix
            }
            digits = s.length() <= 10 ? defaultCountryCode + s : s;
        }
        if (!digits.matches("[1-9]\\d{7,14}")) {
            throw new InvalidPhoneException();
        }
        // India: 10-digit mobiles starting with 6-9
        if (digits.startsWith("91") && !digits.matches("91[6-9]\\d{9}")) {
            throw new InvalidPhoneException();
        }
        return "+" + digits;
    }

    /** "+919876543210" → "+91 ••••••3210" */
    public static String mask(String e164) {
        int keep = 4;
        String countryCode = e164.startsWith("+91") ? "+91" : e164.substring(0, Math.min(3, e164.length()));
        String rest = e164.substring(countryCode.length());
        return countryCode + " " + "•".repeat(Math.max(0, rest.length() - keep)) + rest.substring(Math.max(0, rest.length() - keep));
    }
}
