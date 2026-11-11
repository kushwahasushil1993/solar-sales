package com.solar.sales.otp;

import com.solar.sales.notification.CompanyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class SmsConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    SmsSender smsSender(@Value("${app.sms.twilio.account-sid:}") String accountSid,
                        @Value("${app.sms.twilio.auth-token:}") String authToken,
                        @Value("${app.sms.twilio.from:}") String from) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank()) {
            return new LoggingSmsSender();
        }
        return new TwilioSmsSender(accountSid.trim(), authToken.trim(), from.trim());
    }

    /**
     * Which OTP channel to use, in order: Twilio Verify (verify-service-sid), Twilio trial template
     * (trial-template, e.g. sms_2fa), otherwise the app's own code and message sent through {@link SmsSender}.
     */
    @Bean
    OtpChannel otpChannel(SmsSender smsSender, PasswordEncoder encoder, CompanyProperties company,
                          @Value("${app.otp.ttl-seconds:300}") long ttlSeconds,
                          @Value("${app.otp.message:Your verification code is {code}. It expires in {minutes} minutes. Do not share it.}")
                          String message,
                          @Value("${app.sms.twilio.account-sid:}") String accountSid,
                          @Value("${app.sms.twilio.auth-token:}") String authToken,
                          @Value("${app.sms.twilio.verify-service-sid:}") String verifyServiceSid,
                          @Value("${app.sms.twilio.from:}") String from,
                          @Value("${app.sms.twilio.trial-template:}") String trialTemplate,
                          @Value("${app.sms.twilio.trial-allow-fixed-code:false}") boolean trialAllowFixedCode) {
        OtpChannel channel;
        boolean twilio = !accountSid.isBlank() && !authToken.isBlank();
        if (twilio && !verifyServiceSid.isBlank()) {
            channel = new TwilioVerifyChannel(RestClient.builder(), TwilioVerifyChannel.BASE_URL,
                    accountSid.trim(), authToken.trim(), verifyServiceSid.trim());
        } else if (twilio && !from.isBlank() && !trialTemplate.isBlank()) {
            channel = new TwilioTrialTemplateChannel(RestClient.builder(), "https://api.twilio.com/2010-04-01",
                    accountSid.trim(), authToken.trim(), from.trim(), trialTemplate.trim(), encoder, trialAllowFixedCode);
        } else {
            if (!verifyServiceSid.isBlank()) {
                log.warn("TWILIO_VERIFY_SERVICE_SID is set but the account SID / auth token are missing; ignoring it");
            }
            channel = new LocalCodeChannel(smsSender, encoder,
                    message.replace("{company}", company.name()), Duration.ofSeconds(ttlSeconds));
        }
        if (channel instanceof LocalCodeChannel && !smsSender.isConfigured()) {
            log.warn("No SMS provider configured: mobile sign-in codes are written to this log instead of being sent. "
                    + "Set the Twilio settings to send real SMS (see README).");
        }
        if (trialAllowFixedCode && channel instanceof TwilioTrialTemplateChannel) {
            log.warn("**** INSECURE TESTING MODE: Twilio's fixed trial code is accepted for mobile sign-in. "
                    + "Anyone who knows it can sign in. Never enable app.sms.twilio.trial-allow-fixed-code on a server. ****");
        }
        log.info("Mobile sign-in codes: {}", channel.description());
        return channel;
    }
}
