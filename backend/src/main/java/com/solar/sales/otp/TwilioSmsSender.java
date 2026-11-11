package com.solar.sales.otp;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Sends SMS through Twilio's REST API (no SDK needed). */
class TwilioSmsSender implements SmsSender {

    private final RestClient client;
    private final String accountSid;
    private final String from;

    TwilioSmsSender(String accountSid, String authToken, String from) {
        this.accountSid = accountSid;
        this.from = from;
        this.client = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01")
                .defaultHeaders(h -> h.setBasicAuth(accountSid, authToken))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(String to, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        // "MG..." is a Messaging Service SID; anything else is a sender number / alphanumeric sender id
        form.add(from.startsWith("MG") ? "MessagingServiceSid" : "From", from);
        form.add("Body", message);
        client.post()
                .uri("/Accounts/{sid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
