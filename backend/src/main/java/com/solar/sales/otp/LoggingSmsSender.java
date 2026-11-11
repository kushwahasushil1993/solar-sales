package com.solar.sales.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development fallback: no SMS provider configured, so the message (and its code) is written to the log. */
class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void send(String to, String message) {
        log.warn("SMS NOT sent (no SMS provider configured) to {}: {}", to, message);
    }
}
