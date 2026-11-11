package com.solar.sales.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioTrialTemplateChannelTest {

    private static final String BASE = "https://api.twilio.test/2010-04-01";
    private static final String SENT = "Your verification code is %s. It expires in 5 minutes. Do not share it. Test message from Twilio.";

    private MockRestServiceServer twilio;
    private TwilioTrialTemplateChannel channel;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        twilio = MockRestServiceServer.bindTo(builder).build();
        channel = new TwilioTrialTemplateChannel(builder, BASE, "ACtest", "secret", "+15005550006", "sms_2fa",
                new BCryptPasswordEncoder(), false);
    }

    @Test
    void sendsTheTemplateAndUsesTheCodeTwilioPicked() {
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("To", "+919876500001", "From", "+15005550006", "Body", "sms_2fa")))
                .andRespond(json("{\"sid\":\"SM1\",\"body\":\"" + SENT.formatted("482913") + "\"}"));
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json?To=%2B919876500001&PageSize=20"))
                .andRespond(json("{\"messages\":[{\"sid\":\"SM1\",\"body\":\"" + SENT.formatted("482913") + "\"},"
                        + "{\"sid\":\"SM0\",\"body\":\"" + SENT.formatted("775201") + "\"}]}"));

        String stored = channel.send("+919876500001");

        assertThat(channel.check("+919876500001", "482913", stored)).isTrue();
        assertThat(channel.check("+919876500001", "775201", stored)).isFalse();
        twilio.verify();
    }

    @Test
    void readsTwilioStoredCopyWhenTheReplyHasOnlyTheTemplateName() {
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json"))
                .andRespond(json("{\"sid\":\"SM2\",\"body\":\"sms_2fa\"}"));
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages/SM2.json"))
                .andRespond(json("{\"sid\":\"SM2\",\"body\":\"" + SENT.formatted("190044") + "\"}"));
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json?To=%2B919876500001&PageSize=20"))
                .andRespond(json("{\"messages\":[]}"));

        assertThat(channel.check("+919876500001", "190044", channel.send("+919876500001"))).isTrue();
    }

    @Test
    void refusesAFixedSampleCode() {
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json"))
                .andRespond(json("{\"sid\":\"SM3\",\"body\":\"" + SENT.formatted("482913") + "\"}"));
        twilio.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json?To=%2B919876500001&PageSize=20"))
                .andRespond(json("{\"messages\":[{\"sid\":\"SM3\",\"body\":\"" + SENT.formatted("482913") + "\"},"
                        + "{\"sid\":\"SM1\",\"body\":\"" + SENT.formatted("482913") + "\"}]}"));

        assertThatThrownBy(() -> channel.send("+919876500001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixed sample code");
    }

    @Test
    void fixedSampleCodeCanBeAllowedForLocalTesting() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TwilioTrialTemplateChannel testing = new TwilioTrialTemplateChannel(builder, BASE, "ACtest", "secret",
                "+15005550006", "sms_2fa", new BCryptPasswordEncoder(), true);
        server.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json"))
                .andRespond(json("{\"sid\":\"SM4\",\"body\":\"" + SENT.formatted("482913") + "\"}"));
        server.expect(requestTo(BASE + "/Accounts/ACtest/Messages.json?To=%2B919876500001&PageSize=20"))
                .andRespond(json("{\"messages\":[{\"sid\":\"SM1\",\"body\":\"" + SENT.formatted("482913") + "\"}]}"));

        assertThat(testing.check("+919876500001", "482913", testing.send("+919876500001"))).isTrue();
        assertThat(testing.description()).contains("INSECURE TESTING MODE");
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
