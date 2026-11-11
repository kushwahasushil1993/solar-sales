package com.solar.sales.security;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.SolarSaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.solar.sales.support.TestSenders;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;


import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class SecurityTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired SolarSaleRepository sales;

    @BeforeEach
    void clean() {
        sales.deleteAll();
        users.deleteAll();
    }

    @Test
    void apiRequiresSignIn() throws Exception {
        mvc.perform(get("/api/sales")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Please sign in"));
    }

    @Test
    void providersArePublic() throws Exception {
        mvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("google"))
                .andExpect(jsonPath("$[0].loginUrl").value("/oauth2/authorization/google"))
                .andExpect(jsonPath("$[1].id").value("github"));
    }

    @Test
    void signInRedirectsToProviderWithCallbackOnSameHost() throws Exception {
        mvc.perform(get("/oauth2/authorization/google").header("Host", "localhost:5173"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location",
                        containsString("redirect_uri=http://localhost:5173/login/oauth2/code/google")));
        mvc.perform(get("/oauth2/authorization/github"))
                .andExpect(header().string("Location", startsWith("https://github.com/login/oauth/authorize")))
                .andExpect(header().string("Location", containsString("user:email")));
    }

    @Test
    void meReturnsSignedInUser() throws Exception {
        AppUser rep = user("rep@test.local", Role.USER);
        mvc.perform(get("/api/auth/me").with(as(rep)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rep@test.local"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void stateChangingRequestsNeedCsrfToken() throws Exception {
        AppUser rep = user("rep@test.local", Role.USER);
        mvc.perform(post("/api/sales").with(as(rep)).contentType(MediaType.APPLICATION_JSON).content(saleJson("INV-C1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Refresh the page")));
    }

    @Test
    void userCanAddAndViewSalesButNotUploadEditOrManage() throws Exception {
        AppUser rep = user("rep@test.local", Role.USER);

        mvc.perform(post("/api/sales").with(as(rep)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(saleJson("INV-U1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByName").value("rep"))
                .andExpect(jsonPath("$.installationStatus").value("INSTALLED"))
                .andExpect(jsonPath("$.installationUpdatedBy").value("rep"));
        mvc.perform(get("/api/sales").param("mine", "true").with(as(rep)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/dashboard").with(as(rep))).andExpect(status().isOk());

        Long id = sales.findAll().getFirst().getId();
        mvc.perform(multipart("/api/sales/upload").file(emptyXlsx()).with(as(rep)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/template").with(as(rep))).andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/export").with(as(rep))).andExpect(status().isForbidden());
        mvc.perform(put("/api/sales/" + id).with(as(rep)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(saleJson("INV-U1"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/sales/" + id).with(as(rep)).with(csrf())).andExpect(status().isForbidden());
        // ...but may update installation progress
        mvc.perform(patch("/api/sales/" + id + "/installation").with(as(rep)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationStatus\":\"SCHEDULED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users").with(as(rep))).andExpect(status().isForbidden());
        mvc.perform(get("/api/uploads").with(as(rep))).andExpect(status().isForbidden());
    }

    @Test
    void roleChangesAndDisablingApplyToExistingSessions() throws Exception {
        AppUser admin = user("boss@test.local", Role.ADMIN);
        AppUser rep = user("rep@test.local", Role.USER);
        RequestPostProcessor repSession = as(rep); // principal still says USER

        mvc.perform(get("/api/sales/template").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/users").with(repSession)).andExpect(status().isForbidden());

        mvc.perform(put("/api/users/" + rep.getId() + "/role").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users").with(repSession)).andExpect(status().isOk());

        mvc.perform(put("/api/users/" + rep.getId() + "/enabled").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sales").with(repSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDemoteOrDisableThemself() throws Exception {
        AppUser admin = user("boss@test.local", Role.ADMIN);
        mvc.perform(put("/api/users/" + admin.getId() + "/role").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + admin.getId() + "/enabled").with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsSession() throws Exception {
        AppUser rep = user("rep@test.local", Role.USER);
        mvc.perform(post("/api/auth/logout").with(as(rep)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void swaggerAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SolarSales API"))
                .andExpect(jsonPath("$.paths['/api/sales/upload'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.session.name").value("JSESSIONID"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private AppUser user(String email, Role role) {
        return users.save(new AppUser(email.substring(0, email.indexOf('@')), email, role));
    }

    private static RequestPostProcessor as(AppUser u) {
        return oauth2Login().oauth2User(new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole()));
    }

    private static String saleJson(String invoice) {
        return """
                {"invoiceNumber":"%s","purchaseDate":"2026-01-10","customerName":"Ramesh",
                 "systemCapacityKw":5,"totalAmount":300000,"amountPaid":100000,
                 "installationStatus":"INSTALLED","installationDate":"2026-01-20"}""".formatted(invoice);
    }

    private static MockMultipartFile emptyXlsx() {
        return new MockMultipartFile("file", "a.xlsx", "application/octet-stream", new byte[]{1});
    }

}
