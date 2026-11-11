package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.notification.EmailMessage;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.support.CapturingEmailSender;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class PasswordResetTest {

    private static final String SAME_MESSAGE = "If an account exists for that email, we've sent a link to reset the password.";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired SolarSaleRepository sales;
    @Autowired NotificationRepository notifications;
    @Autowired CapturingEmailSender mail;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from password_reset_tokens");
        notifications.deleteAll();
        sales.deleteAll();
        users.deleteAll();
        mail.reset();
        AppUser user = new AppUser("Sushil Kumar", "sushil@example.com", Role.ADMIN);
        user.setPasswordHash(encoder.encode("OldPass123"));
        users.save(user);
    }

    @Test
    void fullResetFlow() throws Exception {
        forgot("Sushil@Example.com").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(SAME_MESSAGE));

        EmailMessage email = mail.sent.getFirst();
        assertThat(email.to()).isEqualTo("sushil@example.com");
        assertThat(email.subject()).isEqualTo("Reset your Test Solar Co password");
        assertThat(email.html()).contains("Hi Sushil,", "Choose a new password", "expires in <span>30</span> minutes");
        assertThat(email.text()).contains("http://localhost/reset-password?token=");
        String token = tokenFrom(email);

        // asking for a link does not block the current password
        login("sushil@example.com", "OldPass123").andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/password/reset").param("token", token))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.email").value("s••••l@example.com"));
        reset(token, "NewPass456").andExpect(status().isNoContent());

        login("sushil@example.com", "OldPass123").andExpect(status().isUnauthorized());
        login("sushil@example.com", "NewPass456").andExpect(status().isNoContent());
        // single use
        reset(token, "Again12345").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").value("This reset link is invalid or has expired. Request a new one."));
        mvc.perform(get("/api/auth/password/reset").param("token", token)).andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void resetSignsOutExistingSessions() throws Exception {
        MockHttpSession session = (MockHttpSession) login("sushil@example.com", "OldPass123")
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        forgot("sushil@example.com");
        reset(tokenFrom(mail.sent.getFirst()), "NewPass456").andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void changingOwnPasswordAlsoEndsSessionsIncludingThisOne() throws Exception {
        MockHttpSession session = (MockHttpSession) login("sushil@example.com", "OldPass123")
                .andReturn().getRequest().getSession();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/account/password")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldPass123\",\"newPassword\":\"NewPass456\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        login("sushil@example.com", "NewPass456").andExpect(status().isNoContent());
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        forgot("nobody@example.com").andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(SAME_MESSAGE));
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void newerLinkReplacesOlderAndRequestsAreRateLimited() throws Exception {
        forgot("sushil@example.com");
        String first = tokenFrom(mail.sent.getFirst());
        forgot("sushil@example.com"); // within the 60 s cooldown: silently ignored
        assertThat(mail.sent).hasSize(1);

        jdbc.update("update password_reset_tokens set created_at = dateadd('MINUTE', -2, created_at)");
        forgot("sushil@example.com");
        assertThat(mail.sent).hasSize(2);
        String second = tokenFrom(mail.sent.getLast());

        reset(first, "NewPass456").andExpect(status().isBadRequest());
        reset(second, "NewPass456").andExpect(status().isNoContent());
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        forgot("sushil@example.com");
        jdbc.update("update password_reset_tokens set expires_at = ?", Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)));
        reset(tokenFrom(mail.sent.getFirst()), "NewPass456").andExpect(status().isBadRequest());
    }

    @Test
    void validatesInputAndNeedsCsrf() throws Exception {
        forgot("sushil@example.com");
        reset(tokenFrom(mail.sent.getFirst()), "weak").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        forgot("not-an-email").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sushil@example.com\"}")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", password));
    }

    private static String tokenFrom(EmailMessage email) {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(email.text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
