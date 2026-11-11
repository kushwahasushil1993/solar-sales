package com.solar.sales.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For Twilio trial accounts, which may only send predefined templates (e.g. "sms_2fa": "Your verification code is
 * 123456. It expires in 5 minutes. Do not share it. Test message from Twilio."). Twilio picks the code, so it is
 * read back from Twilio's copy of the sent message and only its hash is stored.
 * <p>
 * Safety: if the template ever repeats an earlier code for the same number (a fixed sample code), sending fails,
 * because a fixed code would let anyone sign in, unless {@code allowFixedCode} is set for local testing.
 */
class TwilioTrialTemplateChannel implements OtpChannel {

    private static final Logger log = LoggerFactory.getLogger(TwilioTrialTemplateChannel.class);

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final RestClient client;
    private final String accountSid;
    private final String from;
    private final String template;
    private final PasswordEncoder encoder;
    private final boolean allowFixedCode;

    TwilioTrialTemplateChannel(RestClient.Builder builder, String baseUrl, String accountSid, String authToken,
                               String from, String template, PasswordEncoder encoder, boolean allowFixedCode) {
        this.allowFixedCode = allowFixedCode;
        this.client = builder.baseUrl(baseUrl).defaultHeaders(h -> h.setBasicAuth(accountSid, authToken)).build();
        this.accountSid = accountSid;
        this.from = from;
        this.template = template;
        this.encoder = encoder;
    }

    @Override
    public String send(String phone) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phone);
        form.add("From", from);
        form.add("Body", template);
        Map<?, ?> sent = client.post().uri("/Accounts/{sid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (sent == null) {
            throw new IllegalStateException("Empty reply from Twilio");
        }
        String messageSid = (String) sent.get("sid");
        String code = codeIn(sent.get("body"));
        if (code == null && messageSid != null) {
            // the create reply may still hold the template name; Twilio's stored copy has the rendered text
            Map<?, ?> stored = client.get().uri("/Accounts/{sid}/Messages/{msg}.json", accountSid, messageSid)
                    .retrieve().body(Map.class);
            code = stored == null ? null : codeIn(stored.get("body"));
        }
        if (code == null) {
            throw new IllegalStateException("Twilio's '" + template + "' message contained no 6-digit code");
        }
        if (repeatsEarlierCode(phone, messageSid, code)) {
            if (allowFixedCode) {
                log.warn("INSECURE, TESTING ONLY: accepting Twilio's fixed trial code for {} "
                        + "(app.sms.twilio.trial-allow-fixed-code=true). Anyone who knows it can sign in.",
                        PhoneNumbers.mask(phone));
                return encoder.encode(code);
            }
            throw new IllegalStateException("Twilio's '" + template + "' template sent the same code as an earlier "
                    + "message, so it is a fixed sample code and cannot be used for sign-in. Use Twilio Verify "
                    + "(TWILIO_VERIFY_SERVICE_SID) or upgrade the Twilio account.");
        }
        return encoder.encode(code);
    }

    private boolean repeatsEarlierCode(String phone, String messageSid, String code) {
        Map<?, ?> page = client.get().uri("/Accounts/{sid}/Messages.json?To={to}&PageSize=20", accountSid, phone)
                .retrieve().body(Map.class);
        if (page == null || !(page.get("messages") instanceof List<?> messages)) {
            return false;
        }
        return messages.stream()
                .filter(m -> m instanceof Map<?, ?>)
                .map(m -> (Map<?, ?>) m)
                .filter(m -> !Objects.equals(m.get("sid"), messageSid))
                .anyMatch(m -> code.equals(codeIn(m.get("body"))));
    }

    private static String codeIn(Object body) {
        if (!(body instanceof String text)) {
            return null;
        }
        Matcher m = CODE.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    @Override
    public boolean check(String phone, String code, String stored) {
        return encoder.matches(code, stored);
    }

    @Override
    public String description() {
        return "Twilio trial template '" + template + "' (Twilio's predefined message; code read from Twilio's reply)"
                + (allowFixedCode ? ". INSECURE TESTING MODE: Twilio's fixed sample code is accepted" : "");
    }
}
