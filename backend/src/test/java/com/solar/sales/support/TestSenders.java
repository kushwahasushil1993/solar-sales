package com.solar.sales.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real email and SMS senders with in-memory fakes. Import it in every @SpringBootTest. */
@TestConfiguration
public class TestSenders {

    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }

    @Bean
    @Primary
    CapturingSmsSender capturingSmsSender() {
        return new CapturingSmsSender();
    }
}
