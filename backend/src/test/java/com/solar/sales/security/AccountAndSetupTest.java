package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.support.TestSenders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Users and passwords are managed only through the database: first-run setup, own password, admin reset. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class AccountAndSetupTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired SolarSaleRepository sales;
    @Autowired NotificationRepository notifications;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        notifications.deleteAll();
        sales.deleteAll();
        users.deleteAll();
    }

    @Test
    void firstAdminCanBeCreatedOnlyOnAFreshInstall() throws Exception {
        mvc.perform(get("/api/setup")).andExpect(status().isOk()).andExpect(jsonPath("$.setupRequired").value(true));

        setupAdmin("Sushil", "Owner@Example.com", "Owner2026x")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("owner@example.com"));
        login("owner@example.com", "Owner2026x").andExpect(status().isNoContent());

        mvc.perform(get("/api/setup")).andExpect(jsonPath("$.setupRequired").value(false));
        setupAdmin("Intruder", "intruder@example.com", "Intrude2026x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Setup is already complete. Sign in instead."));
    }

    @Test
    void setupIsClosedWhenAnyAdminExistsEvenWithoutPassword() throws Exception {
        users.save(new AppUser("Existing", "existing@example.com", Role.ADMIN)); // e.g. Google-only admin
        setupAdmin("Intruder", "intruder@example.com", "Intrude2026x").andExpect(status().isForbidden());
    }

    @Test
    void setupValidatesInput() throws Exception {
        setupAdmin("", "bad", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void userChangesOwnPassword() throws Exception {
        AppUser rep = withPassword(new AppUser("Rep", "rep@example.com", Role.USER), "OldPass123");

        changePassword(rep, "WrongPass1", "NewPass456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("Current password is incorrect"));
        changePassword(rep, "OldPass123", "weak").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        changePassword(rep, "OldPass123", "NewPass456").andExpect(status().isNoContent());

        login("rep@example.com", "NewPass456").andExpect(status().isNoContent());
        login("rep@example.com", "OldPass123").andExpect(status().isUnauthorized());
    }

    @Test
    void accountWithoutPasswordCanSetOneWithoutCurrent() throws Exception {
        AppUser googleOnly = users.save(new AppUser("Google User", "g@example.com", Role.ADMIN));
        changePassword(googleOnly, null, "Fresh2026x").andExpect(status().isNoContent());
        login("g@example.com", "Fresh2026x").andExpect(status().isNoContent());
    }

    @Test
    void mobileOnlyAccountNeedsAnEmailForAPassword() throws Exception {
        AppUser phone = users.save(AppUser.withPhone("Phone User", "+919876543210"));
        changePassword(phone, null, "Fresh2026x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void adminResetsAnotherUsersPassword() throws Exception {
        AppUser admin = withPassword(new AppUser("Admin", "admin@example.com", Role.ADMIN), "Admin2026x");
        AppUser rep = withPassword(new AppUser("Rep", "rep@example.com", Role.USER), "Forgot123x");

        resetPassword(admin, rep.getId(), "Temp2026x").andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordLogin").value(true));
        login("rep@example.com", "Temp2026x").andExpect(status().isNoContent());

        resetPassword(admin, admin.getId(), "Other2026x").andExpect(status().isForbidden()); // use My account
        resetPassword(rep, admin.getId(), "Hijack2026x").andExpect(status().isForbidden());  // not an admin
    }

    @Test
    void passwordEndpointsNeedSignInAndCsrf() throws Exception {
        mvc.perform(put("/api/account/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"Fresh2026x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/setup/admin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"A\",\"email\":\"a@example.com\",\"password\":\"Fresh2026x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private AppUser withPassword(AppUser user, String password) {
        user.setPasswordHash(encoder.encode(password));
        return users.save(user);
    }

    private ResultActions setupAdmin(String name, String email, String password) throws Exception {
        return mvc.perform(post("/api/setup/admin").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", password));
    }

    private ResultActions changePassword(AppUser user, String current, String next) throws Exception {
        String body = current == null ? "{\"newPassword\":\"%s\"}".formatted(next)
                : "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next);
        return mvc.perform(put("/api/account/password").with(as(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions resetPassword(AppUser actor, Long targetId, String password) throws Exception {
        return mvc.perform(put("/api/users/" + targetId + "/password").with(as(actor)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"%s\"}".formatted(password)));
    }

    /** A session as the account is now (current password version; older sessions are signed out). */
    private RequestPostProcessor as(AppUser u) {
        AppUser now = users.findById(u.getId()).orElseThrow();
        return oauth2Login().oauth2User(new AuthUser(now.getId(), now.getEmail(), now.getFullName(), now.getRole(),
                now.getPasswordVersion()));
    }
}
