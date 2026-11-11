package com.solar.sales.otp;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCodeChannelTest {

    @Test
    void messageUsesTheConfiguredText() {
        LocalCodeChannel channel = new LocalCodeChannel(new LoggingSmsSender(), new BCryptPasswordEncoder(),
                "Your verification code is {code}. It expires in {minutes} minutes. Do not share it.", Duration.ofMinutes(5));

        assertThat(channel.message("123456"))
                .isEqualTo("Your verification code is 123456. It expires in 5 minutes. Do not share it.");
    }

    @Test
    void storesOnlyAHashOfTheCode() throws Exception {
        StringBuilder sent = new StringBuilder();
        SmsSender capture = new SmsSender() {
            public boolean isConfigured() { return true; }
            public void send(String to, String message) { sent.append(message); }
        };
        LocalCodeChannel channel = new LocalCodeChannel(capture, new BCryptPasswordEncoder(), "Code {code}", Duration.ofMinutes(5));

        String stored = channel.send("+919876500001");
        String code = sent.substring("Code ".length());

        assertThat(stored).doesNotContain(code);
        assertThat(channel.check("+919876500001", code, stored)).isTrue();
        assertThat(channel.check("+919876500001", code.equals("000000") ? "111111" : "000000", stored)).isFalse();
    }

    @Test
    void templateMustContainTheCode() {
        assertThatThrownBy(() -> new LocalCodeChannel(new LoggingSmsSender(), new BCryptPasswordEncoder(), "No code here", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
