package com.solar.sales.service;

import com.solar.sales.dto.SaleRequest;
import com.solar.sales.dto.SaleResponse;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.NotificationType;
import com.solar.sales.entity.Role;
import com.solar.sales.notification.NotificationService;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.support.CapturingEmailSender;
import com.solar.sales.support.TestSenders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sales users can move a sale through the installation workflow (and only that). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class InstallationUpdateTest {

    @Autowired MockMvc mvc;
    @Autowired SaleService saleService;
    @Autowired NotificationService notificationService;
    @Autowired SolarSaleRepository sales;
    @Autowired NotificationRepository notifications;
    @Autowired AppUserRepository users;
    @Autowired CapturingEmailSender mail;

    private AppUser rep;
    private SaleResponse sale;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        sales.deleteAll();
        users.deleteAll();
        mail.reset();
        rep = users.save(new AppUser("Ravi Rep", "ravi@test.local", Role.USER));
        AuthUser admin = new AuthUser(999L, "boss@test.local", "Anita Admin", Role.ADMIN);
        sale = saleService.create(new SaleRequest("INV-I1", LocalDate.now().minusDays(10), "Kavita Joshi", null,
                "kavita@example.com", "Pune", "Maharashtra", "Monocrystalline", new BigDecimal("5"), 10, "Luminous",
                new BigDecimal("300000"), BigDecimal.ZERO, InstallationStatus.PENDING, null, null), admin, false);
    }

    @Test
    void salesUserMovesTheSaleThroughTheWorkflow() throws Exception {
        LocalDate day = LocalDate.now().plusDays(2);
        update("SCHEDULED", day.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.installationDate").value(day.toString()))
                .andExpect(jsonPath("$.installationUpdatedBy").value("Ravi Rep"))
                .andExpect(jsonPath("$.installationUpdatedAt").exists());
        update("IN_PROGRESS", day.toString()).andExpect(status().isOk());
        update("INSTALLED", null)
                .andExpect(jsonPath("$.installationStatus").value("INSTALLED"))
                .andExpect(jsonPath("$.installationDate").value(LocalDate.now().toString())); // defaults to today

        assertThat(notificationService.forSale(sale.id()).reversed())
                .extracting(n -> n.type() + " by " + n.triggeredBy())
                .containsExactly("INSTALLATION_SCHEDULED by Ravi Rep", "INSTALLATION_IN_PROGRESS by Ravi Rep",
                        "INSTALLATION_COMPLETED by Ravi Rep");
        // amounts and customer details are untouched
        assertThat(sales.findById(sale.id()).orElseThrow().getTotalAmount()).isEqualByComparingTo("300000");
    }

    @Test
    void sameStatusOrNotifyFalseSendsNoEmail() throws Exception {
        update("SCHEDULED", null).andExpect(status().isOk());
        update("SCHEDULED", LocalDate.now().plusDays(5).toString()).andExpect(status().isOk()); // date only
        mvc.perform(patch("/api/sales/" + sale.id() + "/installation").param("notify", "false")
                        .with(asRep()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        assertThat(notificationService.forSale(sale.id())).extracting(n -> n.type())
                .containsExactly(NotificationType.INSTALLATION_SCHEDULED);
    }

    @Test
    void installationCannotBeBeforePurchase() throws Exception {
        update("INSTALLED", LocalDate.now().minusDays(30).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.installationDate").value("Installation date cannot be before the purchase date"));
    }

    @Test
    void statusIsRequiredAndSaleMustExist() throws Exception {
        mvc.perform(patch("/api/sales/" + sale.id() + "/installation").with(asRep()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.installationStatus").value("Installation status is required"));
        mvc.perform(patch("/api/sales/999999/installation").with(asRep()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationStatus\":\"SCHEDULED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void needsSignInAndCsrf() throws Exception {
        mvc.perform(patch("/api/sales/" + sale.id() + "/installation")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationStatus\":\"SCHEDULED\"}"))
                .andExpect(status().isForbidden()); // CSRF checked first
        mvc.perform(patch("/api/sales/" + sale.id() + "/installation").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"installationStatus\":\"SCHEDULED\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions update(String status, String date) throws Exception {
        String body = date == null ? "{\"installationStatus\":\"%s\"}".formatted(status)
                : "{\"installationStatus\":\"%s\",\"installationDate\":\"%s\"}".formatted(status, date);
        return mvc.perform(patch("/api/sales/" + sale.id() + "/installation").with(asRep()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asRep() {
        return oauth2Login().oauth2User(new AuthUser(rep.getId(), rep.getEmail(), rep.getFullName(), rep.getRole()));
    }
}
