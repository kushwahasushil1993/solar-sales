package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.support.TestSenders;
import com.solar.sales.task.InboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-ups get no role until an admin approves them as sales or agent. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class ApprovalTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired InboxRepository inbox;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        inbox.deleteAll();
        users.deleteAll();
        admin = users.save(new AppUser("Anita Admin", "admin@test.local", Role.ADMIN));
    }

    @Test
    void signUpAsAgentWaitsForTheAdminWhoDecides() throws Exception {
        register("Ravi Installer", "ravi@example.com", "AGENT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PENDING"))
                .andExpect(jsonPath("$.requestedRole").value("AGENT"));

        mvc.perform(get("/api/inbox").with(as(admin)))
                .andExpect(jsonPath("$.items[0].type").value("ACCOUNT_PENDING"))
                .andExpect(jsonPath("$.items[0].message",
                        containsString("Ravi Installer (ravi@example.com) signed up and wants to join an installation agent")));

        MockHttpSession ravi = login("ravi@example.com");
        mvc.perform(get("/api/auth/me").session(ravi)).andExpect(jsonPath("$.role").value("PENDING"));
        for (String blocked : new String[]{"/api/sales", "/api/dashboard", "/api/tasks/mine", "/api/tasks/available",
                "/api/inbox", "/api/users", "/api/agents"}) {
            mvc.perform(get(blocked).session(ravi)).andExpect(status().isForbidden());
        }

        // the admin decides, and the change applies to the existing session
        Long id = users.findByEmail("ravi@example.com").orElseThrow().getId();
        mvc.perform(put("/api/users/" + id + "/role").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"AGENT\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tasks/mine").session(ravi)).andExpect(status().isOk());
        mvc.perform(get("/api/sales").session(ravi)).andExpect(status().isForbidden()); // agents don't see sales
    }

    @Test
    void adminCanMakeASalesSignUpAnAgentOrViceVersa() throws Exception {
        register("Priya", "priya@example.com", "AGENT");
        Long id = users.findByEmail("priya@example.com").orElseThrow().getId();
        mvc.perform(put("/api/users/" + id + "/role").with(as(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}")).andExpect(status().isOk());

        MockHttpSession priya = login("priya@example.com");
        mvc.perform(get("/api/sales").session(priya)).andExpect(status().isOk());
        mvc.perform(get("/api/tasks/mine").session(priya)).andExpect(status().isForbidden());
    }

    @Test
    void askingForAdminIsIgnored() throws Exception {
        register("Sneaky", "sneaky@example.com", "ADMIN")
                .andExpect(jsonPath("$.role").value("PENDING"))
                .andExpect(jsonPath("$.requestedRole").doesNotExist());
    }

    @Test
    void usersListShowsWhoIsWaiting() throws Exception {
        register("Meena", "meena@example.com", "USER");
        mvc.perform(get("/api/users").with(as(admin)))
                .andExpect(jsonPath("$[?(@.email == 'meena@example.com')].role").value("PENDING"))
                .andExpect(jsonPath("$[?(@.email == 'meena@example.com')].requestedRole").value("USER"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String name, String email, String role)
            throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"secret123\",\"requestedRole\":\"%s\"}"
                        .formatted(name, email, role)));
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "secret123"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession();
    }

    private RequestPostProcessor as(AppUser u) {
        AppUser now = users.findById(u.getId()).orElseThrow();
        return oauth2Login().oauth2User(new AuthUser(now.getId(), now.getEmail(), now.getFullName(), now.getRole(),
                now.getPasswordVersion()));
    }

}
