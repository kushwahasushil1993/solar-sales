package com.solar.sales.otp;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Duration;

/** The app generates the code, texts it with its own message (via {@link SmsSender}) and stores only a hash. */
class LocalCodeChannel implements OtpChannel {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsSender sms;
    private final PasswordEncoder encoder;
    private final String messageTemplate;
    private final Duration ttl;

    /** @param messageTemplate SMS text with {code}, optionally {minutes} and {company} (app.otp.message) */
    LocalCodeChannel(SmsSender sms, PasswordEncoder encoder, String messageTemplate, Duration ttl) {
        if (!messageTemplate.contains("{code}")) {
            throw new IllegalArgumentException("app.otp.message must contain {code}");
        }
        this.sms = sms;
        this.encoder = encoder;
        this.messageTemplate = messageTemplate;
        this.ttl = ttl;
    }

    String message(String code) {
        return messageTemplate.replace("{code}", code).replace("{minutes}", String.valueOf(ttl.toMinutes()));
    }

    @Override
    public String send(String phone) throws Exception {
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        sms.send(phone, message(code));
        return encoder.encode(code);
    }

    @Override
    public boolean check(String phone, String code, String stored) {
        return encoder.matches(code, stored);
    }

    @Override
    public String description() {
        return sms.isConfigured() ? "own SMS text via Twilio Messaging" : "codes written to this log (no SMS provider)";
    }
}
