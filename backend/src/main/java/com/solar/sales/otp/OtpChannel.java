package com.solar.sales.otp;

/**
 * How one-time codes are delivered and checked. Rate limits, attempt counting, expiry and the
 * new-number/existing-account logic stay in {@link OtpService} whichever channel is used.
 */
public interface OtpChannel {

    /** Sends a new code to {@code phone}; returns what to store for {@link #check} (a hash, or a marker). */
    String send(String phone) throws Exception;

    /** True if {@code code} is correct for the latest code sent to {@code phone}. */
    boolean check(String phone, String code, String stored) throws Exception;

    /** For the startup log. */
    String description();
}
