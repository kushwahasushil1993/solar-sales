package com.solar.sales.otp;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Twilio Verify: Twilio generates, texts (with its predefined OTP message) and checks the code. Works on Twilio
 * trial accounts, which cannot send custom SMS text. Remove TWILIO_VERIFY_SERVICE_SID to use our own text again.
 */
class TwilioVerifyChannel implements OtpChannel {

    static final String MARKER = "twilio-verify"; // stored instead of a hash: Twilio holds the code
    static final String BASE_URL = "https://verify.twilio.com/v2";

    private final RestClient client;
    private final String serviceSid;

    TwilioVerifyChannel(RestClient.Builder builder, String baseUrl, String accountSid, String authToken, String serviceSid) {
        this.serviceSid = serviceSid;
        this.client = builder.baseUrl(baseUrl).defaultHeaders(h -> h.setBasicAuth(accountSid, authToken)).build();
    }

    @Override
    public String send(String phone) {
        client.post().uri("/Services/{sid}/Verifications", serviceSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form("To", phone, "Channel", "sms"))
                .retrieve()
                .toBodilessEntity();
        return MARKER;
    }

    @Override
    public boolean check(String phone, String code, String stored) {
        try {
            Map<?, ?> result = client.post().uri("/Services/{sid}/VerificationCheck", serviceSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form("To", phone, "Code", code))
                    .retrieve()
                    .body(Map.class);
            return result != null && "approved".equals(result.get("status"));
        } catch (HttpClientErrorException.NotFound e) {
            return false; // Twilio: no pending verification (expired, already approved, or too many attempts)
        }
    }

    @Override
    public String description() {
        return "Twilio Verify, Twilio's predefined OTP message (service " + serviceSid + ")";
    }

    private static MultiValueMap<String, String> form(String k1, String v1, String k2, String v2) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(k1, v1);
        form.add(k2, v2);
        return form;
    }
}
