package com.solar.sales.support;

import com.solar.sales.otp.SmsSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CapturingSmsSender implements SmsSender {

    public record Sms(String to, String message) {
    }

    public final List<Sms> sent = new CopyOnWriteArrayList<>();

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(String to, String message) {
        sent.add(new Sms(to, message));
    }

    /** The 6-digit code from the latest SMS. */
    public String lastCode() {
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(sent.getLast().message());
        if (!m.find()) {
            throw new IllegalStateException("No code in " + sent.getLast().message());
        }
        return m.group(1);
    }

    public void reset() {
        sent.clear();
    }
}
