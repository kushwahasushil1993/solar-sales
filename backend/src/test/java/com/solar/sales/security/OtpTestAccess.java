package com.solar.sales.security;

import java.time.Instant;

/** Lets tests build the session attribute that marks a verified, not-yet-registered number. */
public final class OtpTestAccess {

    private OtpTestAccess() {
    }

    public static String verifiedPhoneKey() {
        return OtpAuthenticationFilter.VERIFIED_PHONE;
    }

    public static Object verifiedAt(String phone, Instant when) {
        return new OtpAuthenticationFilter.VerifiedPhone(phone, when);
    }
}
