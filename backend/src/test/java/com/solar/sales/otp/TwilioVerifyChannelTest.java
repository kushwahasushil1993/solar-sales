package com.solar.sales.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioVerifyChannelTest {

    private static final String BASE = "https://verify.twilio.test/v2";
    private static final String AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("ACtest:secret".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer twilio;
    private TwilioVerifyChannel channel;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        twilio = MockRestServiceServer.bindTo(builder).build();
        channel = new TwilioVerifyChannel(builder, BASE, "ACtest", "secret", "VAservice");
    }

    @Test
    void sendStartsASmsVerification() {
        twilio.expect(requestTo(BASE + "/Services/VAservice/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", AUTH))
                .andExpect(content().formDataContains(java.util.Map.of("To", "+919876500001", "Channel", "sms")))
                .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        assertThat(channel.send("+919876500001")).isEqualTo(TwilioVerifyChannel.MARKER);
        twilio.verify();
    }

    @Test
    void approvedCheckMeansCorrectCode() {
        twilio.expect(requestTo(BASE + "/Services/VAservice/VerificationCheck"))
                .andExpect(content().formDataContains(java.util.Map.of("To", "+919876500001", "Code", "123456")))
                .andRespond(withSuccess("{\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
        assertThat(channel.check("+919876500001", "123456", TwilioVerifyChannel.MARKER)).isTrue();
    }

    @Test
    void pendingOrMissingVerificationMeansWrongCode() {
        twilio.expect(requestTo(BASE + "/Services/VAservice/VerificationCheck"))
                .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON));
        twilio.expect(requestTo(BASE + "/Services/VAservice/VerificationCheck"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(channel.check("+919876500001", "000000", TwilioVerifyChannel.MARKER)).isFalse();
        assertThat(channel.check("+919876500001", "000000", TwilioVerifyChannel.MARKER)).isFalse();
    }

    @Test
    void twilioRejectionsSurfaceAsErrors() {
        twilio.expect(requestTo(BASE + "/Services/VAservice/Verifications"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":60200}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> channel.send("+919876500001")).isInstanceOf(HttpClientErrorException.class);
    }
}
