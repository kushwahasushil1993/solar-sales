package com.solar.sales.otp;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.support.CapturingSmsSender;
import com.solar.sales.support.TestSenders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class OtpAuthTest {

    private static final String PHONE = "+919876543210";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired OtpChallengeRepository challenges;
    @Autowired CapturingSmsSender sms;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        challenges.deleteAll();
        users.deleteAll();
        sms.reset();
    }

    @Test
    void existingAccountGoesStraightIn() throws Exception {
        users.save(AppUser.withPhone("Meera", PHONE));

        requestCode("+91 98765 43210")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.maskedPhone").value("+91 ••••••3210"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendInSeconds").value(30));
        assertThat(sms.sent).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(PHONE);
            assertThat(m.message()).matches("Your verification code is \\d{6}\\. It expires in 5 minutes\\. Do not share it\\.");
        });

        MvcResult result = verify("9876543210", sms.lastCode()).andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Meera"))
                .andExpect(jsonPath("$.provider").value("phone"));
    }

    @Test
    void newNumberGetsACodeThenCompletesRegistration() throws Exception {
        // no "account not found": any valid number gets a code
        requestCode("98765 43210").andExpect(status().isOk());
        assertThat(sms.sent).hasSize(1);

        MvcResult verified = verify("9876543210", sms.lastCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.maskedPhone").value("+91 ••••••3210"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", org.hamcrest.Matchers.containsString("UTF-8")))
                .andReturn();
        MockHttpSession session = (MockHttpSession) verified.getRequest().getSession();
        assertThat(users.findByPhone(PHONE)).as("no account until details are submitted").isEmpty();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());

        MvcResult registered = register(session, "Ravi Phone", "Ravi@Example.com")
                .andExpect(status().isNoContent()).andReturn();
        MockHttpSession signedIn = (MockHttpSession) registered.getRequest().getSession();

        mvc.perform(get("/api/auth/me").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ravi Phone"))
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.email").value("ravi@example.com"))
                .andExpect(jsonPath("$.role").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("phone"));
        mvc.perform(get("/api/sales").session(signedIn)).andExpect(status().isForbidden()); // until approved
    }

    @Test
    void emailIsOptionalAndNeverGrantsAdmin() throws Exception {
        MockHttpSession session = verifiedNewNumber();
        // an email typed during phone registration never makes anyone admin
        MvcResult r = register(session, "Not Boss", "boss@test.local").andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/auth/me").session((MockHttpSession) r.getRequest().getSession()))
                .andExpect(jsonPath("$.role").value("PENDING"));

        clean();
        MockHttpSession second = verifiedNewNumber();
        register(second, "No Email", "").andExpect(status().isNoContent());
        assertThat(users.findByPhone(PHONE).orElseThrow().getEmail()).isNull();
    }

    @Test
    void registrationDetailsAreValidated() throws Exception {
        users.save(new AppUser("Taken", "taken@example.com", Role.USER));
        MockHttpSession session = verifiedNewNumber();

        register(session, "  ", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").value("Full name is required"));
        register(session, "Ravi", "not-an-email").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email is not valid"));
        register(session, "Ravi", "taken@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").exists());
        // still verified after a validation error: fix the form and submit again
        register(session, "Ravi", null).andExpect(status().isNoContent());
    }

    @Test
    void registrationNeedsAVerifiedNumberAndIsSingleUse() throws Exception {
        register(new MockHttpSession(), "Sneaky", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Please verify your mobile number first."));

        MockHttpSession session = verifiedNewNumber();
        MvcResult first = register(session, "Ravi", null).andExpect(status().isNoContent()).andReturn();
        register((MockHttpSession) first.getRequest().getSession(), "Ravi Again", null)
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void verifiedNumberExpiresAfterTenMinutes() throws Exception {
        MockHttpSession session = verifiedNewNumber();
        session.setAttribute(com.solar.sales.security.OtpTestAccess.verifiedPhoneKey(),
                com.solar.sales.security.OtpTestAccess.verifiedAt(PHONE, java.time.Instant.now().minusSeconds(11 * 60)));
        register(session, "Late", null).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCodesCountDownThenLockTheCode() throws Exception {
        requestCode(PHONE);
        String right = sms.lastCode();
        String wrong = right.equals("000000") ? "111111" : "000000";

        verify(PHONE, wrong).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect code. 4 attempts left."));
        for (int i = 0; i < 3; i++) {
            verify(PHONE, wrong).andExpect(status().isUnauthorized());
        }
        verify(PHONE, wrong).andExpect(jsonPath("$.detail").value("Too many incorrect attempts. Request a new code."));
        verify(PHONE, right).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("No active code for this number. Request a new code."));
    }

    @Test
    void codesAreSingleUseAndExpire() throws Exception {
        users.save(AppUser.withPhone("Meera", PHONE));
        requestCode(PHONE);
        String code = sms.lastCode();
        verify(PHONE, code).andExpect(status().isNoContent());
        verify(PHONE, code).andExpect(status().isUnauthorized());

        backdateCodes(2);
        requestCode(PHONE).andExpect(status().isOk());
        jdbc.update("update otp_challenges set expires_at = ?", Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)));
        verify(PHONE, sms.lastCode()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("This code has expired. Request a new one."));
    }

    @Test
    void resendIsRateLimitedAndOnlyTheNewestCodeWorks() throws Exception {
        users.save(AppUser.withPhone("Meera", PHONE));
        requestCode(PHONE).andExpect(status().isOk());
        String first = sms.lastCode();

        requestCode(PHONE).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.startsWith("Please wait")));

        backdateCodes(1);
        requestCode(PHONE).andExpect(status().isOk());
        String second = sms.lastCode();
        if (!first.equals(second)) {
            verify(PHONE, first).andExpect(status().isUnauthorized());
        }
        verify(PHONE, second).andExpect(status().isNoContent());
    }

    @Test
    void hourlyLimitPerNumber() throws Exception {
        for (int i = 0; i < 5; i++) {
            backdateCodes(1);
            requestCode(PHONE).andExpect(status().isOk());
        }
        backdateCodes(1);
        requestCode(PHONE).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many codes requested for this number. Try again in an hour."));
    }

    @Test
    void disabledAccountCannotGetOrUseACode() throws Exception {
        AppUser meera = users.save(AppUser.withPhone("Meera", PHONE));
        requestCode(PHONE).andExpect(status().isOk());
        meera.setEnabled(false);
        users.save(meera);

        verify(PHONE, sms.lastCode()).andExpect(status().isForbidden());
        backdateCodes(1);
        requestCode(PHONE).andExpect(status().isForbidden());
    }

    @Test
    void invalidNumberIsA400WithFieldError() throws Exception {
        requestCode("12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.phone").exists());
        assertThat(sms.sent).isEmpty();
    }

    @Test
    void allStepsNeedCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\"}".formatted(PHONE))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/otp/verify").param("phone", PHONE).param("code", "123456"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/otp/register").param("fullName", "X")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions requestCode(String phone) throws Exception {
        return mvc.perform(post("/api/auth/otp/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\"}".formatted(phone)));
    }

    private ResultActions verify(String phone, String code) throws Exception {
        return mvc.perform(post("/api/auth/otp/verify").with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("phone", phone).param("code", code));
    }

    private ResultActions register(MockHttpSession session, String fullName, String email) throws Exception {
        var req = post("/api/auth/otp/register").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("fullName", fullName);
        if (email != null) {
            req.param("email", email);
        }
        return mvc.perform(req);
    }

    private MockHttpSession verifiedNewNumber() throws Exception {
        backdateCodes(1);
        requestCode(PHONE).andExpect(status().isOk());
        MvcResult r = verify(PHONE, sms.lastCode()).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    /** Moves existing codes into the past so the resend cooldown has elapsed. */
    private void backdateCodes(int minutes) {
        jdbc.update("update otp_challenges set created_at = dateadd('MINUTE', ?, created_at)", -minutes);
    }
}
