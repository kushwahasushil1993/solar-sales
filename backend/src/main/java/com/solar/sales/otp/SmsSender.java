package com.solar.sales.otp;

public interface SmsSender {

    /** False when no SMS provider is configured: messages (including codes) only go to the server log. */
    boolean isConfigured();

    /** @param to E.164 number, e.g. +919876543210 */
    void send(String to, String message) throws Exception;
}
