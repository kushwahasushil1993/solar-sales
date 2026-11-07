package com.solar.sales.security;

import com.jayway.jsonpath.JsonPath;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.SolarSaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    // matches src/test/resources/application.properties
    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@Test1";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired SolarSaleRepository sales;

    @BeforeEach
    void clean() {
        sales.deleteAll();
    }

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/sales")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sales").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCreatesPlainUser() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Priya Patel", email, "secret123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("USER"));

        // same email again (different case) is rejected
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Other", email.toUpperCase(), "secret123")))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationValidatesPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Weak", uniqueEmail(), "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must contain a letter and a number"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong-pass1\"}".formatted(ADMIN_EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void userCanAddAndViewSalesButNotUploadEditOrManage() throws Exception {
        String token = register("Sales Rep");

        mvc.perform(post("/api/sales").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(saleJson("INV-U1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByName").value("Sales Rep"))
                .andExpect(jsonPath("$.installationStatus").value("PENDING"))
                .andExpect(jsonPath("$.installationDate").doesNotExist());
        mvc.perform(get("/api/sales").param("mine", "true").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/dashboard").header("Authorization", bearer(token))).andExpect(status().isOk());

        Long id = sales.findAll().getFirst().getId();
        mvc.perform(multipart("/api/sales/upload").file(emptyXlsx()).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/template").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/export").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(put("/api/sales/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(saleJson("INV-U1"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/sales/" + id).header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/uploads").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUploadAndManageUsers() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/api/sales/template").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", bearer(admin))).andExpect(status().isOk());

        String userToken = register("Promote Me");
        Long userId = JsonPath.<Integer>read(meJson(userToken), "$.id").longValue();

        mvc.perform(put("/api/users/" + userId + "/role").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        // role change applies to the existing token immediately
        mvc.perform(get("/api/users").header("Authorization", bearer(userToken))).andExpect(status().isOk());

        mvc.perform(put("/api/users/" + userId + "/enabled").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        // disabled accounts lose access straight away and cannot log in
        mvc.perform(get("/api/sales").header("Authorization", bearer(userToken))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDemoteOrDisableThemself() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        Long adminId = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mvc.perform(put("/api/users/" + adminId + "/role").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + adminId + "/enabled").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private String register(String name) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(name, uniqueEmail(), "secret123")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String meJson(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    private static String registerJson(String name, String email, String password) {
        return "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password);
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
