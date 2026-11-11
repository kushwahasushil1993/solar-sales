package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.solar.sales.support.TestSenders;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class PasswordAuthTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void registerCreatesAccountWaitingForApproval() throws Exception {
        register("Priya Patel", "Priya@Example.com", "secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("priya@example.com"))
                .andExpect(jsonPath("$.role").value("PENDING"))
                .andExpect(jsonPath("$.passwordLogin").value(true))
                .andExpect(jsonPath("$.emailVerified").value(false));

        register("Again", "PRIYA@example.com", "secret123")
                .andExpect(status().isConflict());
    }

    @Test
    void registerValidatesInput() throws Exception {
        register("Weak", "weak@example.com", "password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must contain a letter and a number"));
        register("", "not-an-email", "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void registeringAnAdminEmailDoesNotGrantAdmin() throws Exception {
        // self-registration always creates a sales user, whatever the email
        register("Not The Boss", "boss@test.local", "secret123").andExpect(status().isCreated());
        MockHttpSession session = login("boss@test.local", "secret123");

        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.role").value("PENDING"));
        mvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void passwordLoginStartsASession() throws Exception {
        register("Ravi", "ravi@example.com", "secret123");
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();

        MvcResult result = mvc.perform(post("/api/auth/login").session(before).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "Ravi@Example.com").param("password", "secret123"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        assertThat(session.getId()).as("session id rotated on login").isNotEqualTo(idBefore);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ravi@example.com"))
                .andExpect(jsonPath("$.provider").value("password"));
        // signed in, but nothing else until an admin approves the account
        mvc.perform(get("/api/sales").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        register("Ravi", "ravi@example.com", "secret123");
        attemptLogin("ravi@example.com", "wrong-pass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        attemptLogin("nobody@example.com", "secret123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        register("Ravi", "ravi@example.com", "secret123");
        AppUser ravi = users.findByEmail("ravi@example.com").orElseThrow();
        ravi.setEnabled(false);
        users.save(ravi);

        attemptLogin("ravi@example.com", "secret123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This account is disabled. Contact an administrator."));
    }

    @Test
    void loginAndRegisterNeedCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "a@example.com").param("password", "secret123"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"email\":\"a@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions register(String name, String email, String password)
            throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions attemptLogin(String email, String password)
            throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", password));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = attemptLogin(email, password).andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }
}
