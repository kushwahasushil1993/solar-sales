package com.solar.sales.support;

import com.solar.sales.notification.EmailMessage;
import com.solar.sales.notification.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CapturingEmailSender implements EmailSender {

    public final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
    public volatile boolean fail;

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(EmailMessage message) {
        if (fail) {
            throw new IllegalStateException("SMTP server unavailable");
        }
        sent.add(message);
    }

    public void reset() {
        sent.clear();
        fail = false;
    }
}
