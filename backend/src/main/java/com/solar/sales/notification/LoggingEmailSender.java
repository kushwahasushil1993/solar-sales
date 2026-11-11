package com.solar.sales.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no SMTP server is configured: nothing leaves the machine, the email is only logged. */
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void send(EmailMessage message) {
        log.info("Email NOT sent (SMTP not configured): to={} subject=\"{}\"\n{}", message.to(), message.subject(),
                message.text());
    }
}
