package com.solar.sales.notification;

public interface EmailSender {

    /** False when no mail server is configured: messages are only logged. */
    boolean isConfigured();

    /** Throws on failure; the message is recorded on the notification. */
    void send(EmailMessage message) throws Exception;
}
