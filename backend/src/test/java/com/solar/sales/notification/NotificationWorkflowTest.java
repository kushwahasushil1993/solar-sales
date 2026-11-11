package com.solar.sales.notification;

import com.solar.sales.dto.NotificationResponse;
import com.solar.sales.dto.SaleRequest;
import com.solar.sales.dto.SaleResponse;
import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.NotificationStatus;
import com.solar.sales.entity.NotificationType;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.AppUserRepository;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.SaleService;
import com.solar.sales.support.CapturingEmailSender;
import com.solar.sales.support.TestSenders;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSenders.class)
class NotificationWorkflowTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "boss@test.local", "Anita Admin", Role.ADMIN);

    @Autowired SaleService saleService;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notifications;
    @Autowired SolarSaleRepository sales;
    @Autowired AppUserRepository users;
    @Autowired CapturingEmailSender mail;
    @Autowired PlatformTransactionManager txManager;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        notifications.deleteAll();
        sales.deleteAll();
        users.deleteAll();
        mail.reset();
    }

    @Test
    void newSaleEmailsTheInvoice() {
        SaleResponse sale = saleService.create(req("INV-N1", "ramesh@example.com", InstallationStatus.PENDING, null, 100000), ADMIN, true);

        List<NotificationResponse> sent = notificationService.forSale(sale.id());
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.INVOICE);
            assertThat(n.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.recipient()).isEqualTo("ramesh@example.com");
            assertThat(n.triggeredBy()).isEqualTo("Anita Admin");
        });
        EmailMessage email = mail.sent.getFirst();
        assertThat(email.to()).isEqualTo("ramesh@example.com");
        assertThat(email.subject()).isEqualTo("Invoice INV-N1 from Test Solar Co");
        assertThat(email.html()).contains("INV-N1", "Test Solar Co", "Dear Ramesh,", "Billed to", "₹3,00,000", "₹1,00,000", "₹2,00,000",
                "Solar PV system, 5 kW Monocrystalline (10 panels)", "Your order progress");
        assertThat(email.text()).contains("Invoice INV-N1", "✓ Order placed",
                        "our team will contact you to schedule the installation")
                .doesNotContain("Installation scheduled", "Installation in progress", "✓ Installed");
        assertThat(email.html()).doesNotContain("Installation scheduled", "Installation in progress");
    }

    @Test
    void invoiceSentLaterDescribesTheCurrentStage() {
        SaleResponse sale = saleService.create(req("INV-N4", "late@example.com", InstallationStatus.INSTALLED,
                LocalDate.now().minusDays(1), 300000), ADMIN, false);

        notificationService.sendNow(sale.id(), NotificationType.INVOICE, "Anita Admin");

        assertThat(mail.sent.getLast().text())
                .contains("Your installation is complete and this invoice is fully paid.")
                .doesNotContain("schedule the installation");
    }

    @Test
    void customerWithoutEmailIsSkippedNotFailed() {
        SaleResponse sale = saleService.create(req("INV-N2", null, InstallationStatus.PENDING, null, 0), ADMIN, true);

        assertThat(notificationService.forSale(sale.id())).singleElement().satisfies(n -> {
            assertThat(n.status()).isEqualTo(NotificationStatus.SKIPPED);
            assertThat(n.detail()).isEqualTo("The customer has no email address");
        });
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void notifyFalseSendsNothing() {
        SaleResponse sale = saleService.create(req("INV-N3", "a@example.com", InstallationStatus.PENDING, null, 0), ADMIN, false);
        saleService.update(sale.id(), req("INV-N3", "a@example.com", InstallationStatus.INSTALLED, LocalDate.now(), 300000), ADMIN, false);

        assertThat(notifications.count()).isZero();
    }

    @Test
    void installationWorkflowEmailsEachStage() {
        SaleResponse sale = saleService.create(req("INV-W1", "priya@example.com", InstallationStatus.PENDING, null, 100000), ADMIN, false);
        LocalDate day = LocalDate.now().plusDays(3);

        saleService.update(sale.id(), req("INV-W1", "priya@example.com", InstallationStatus.SCHEDULED, day, 100000), ADMIN, true);
        saleService.update(sale.id(), req("INV-W1", "priya@example.com", InstallationStatus.SCHEDULED, day, 100000), ADMIN, true); // no change
        saleService.update(sale.id(), req("INV-W1", "priya@example.com", InstallationStatus.IN_PROGRESS, day, 100000), ADMIN, true);
        saleService.update(sale.id(), req("INV-W1", "priya@example.com", InstallationStatus.IN_PROGRESS, day, 200000), ADMIN, true); // payment
        saleService.update(sale.id(), req("INV-W1", "priya@example.com", InstallationStatus.INSTALLED, day, 300000), ADMIN, true); // done + paid

        assertThat(notificationService.forSale(sale.id()).reversed()).extracting(NotificationResponse::type).containsExactly(
                NotificationType.INSTALLATION_SCHEDULED,
                NotificationType.INSTALLATION_IN_PROGRESS,
                NotificationType.PAYMENT_RECEIVED,
                NotificationType.INSTALLATION_COMPLETED,
                NotificationType.PAYMENT_RECEIVED); // every amount change sends the invoice, also alongside completion

        EmailMessage scheduled = mail.sent.getFirst();
        assertThat(scheduled.subject()).isEqualTo("Your solar installation is scheduled (INV-W1)");
        assertThat(scheduled.text()).contains("scheduled for " + day.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM uuuu", java.util.Locale.ENGLISH)));

        EmailMessage payment = mail.sent.get(2);
        assertThat(payment.text()).contains("Amount paid so far: ₹2,00,000", "Balance due: ₹1,00,000",
                "Invoice INV-W1", "Total: ₹3,00,000");
        assertThat(payment.html()).contains("Billed to", "₹2,00,000");

        EmailMessage completed = mail.sent.get(3);
        assertThat(completed.subject()).isEqualTo("Your solar system is installed (INV-W1)");
        assertThat(completed.html()).contains("Installation completed", "fully paid");
        assertThat(completed.text()).contains("✓ Order placed", "✓ Installation scheduled",
                "✓ Installation in progress", "✓ Installed");

        // status emails carry no invoice; only invoice/amount emails do
        for (EmailMessage later : List.of(mail.sent.get(0), mail.sent.get(1), completed)) {
            assertThat(later.html()).doesNotContain("Billed to", "Billed by", "Invoice <span>", "Total</td>");
            assertThat(later.text()).doesNotContain("Total: ", "Balance due: ₹0");
        }

        // the scheduled email lists only the stages reached so far
        assertThat(scheduled.text()).contains("✓ Order placed", "✓ Installation scheduled")
                .doesNotContain("Installation in progress", "✓ Installed");
    }

    @Test
    void anyAmountChangeEmailsTheFullInvoice() {
        SaleResponse sale = saleService.create(req("INV-A1", "amt@example.com", InstallationStatus.PENDING, null, 100000), ADMIN, false);

        // total changed (e.g. extra panels) → updated invoice
        saleService.update(sale.id(), reqTotal("INV-A1", "amt@example.com", 350000, 100000), ADMIN, true);
        // paid corrected down → updated invoice
        saleService.update(sale.id(), reqTotal("INV-A1", "amt@example.com", 350000, 50000), ADMIN, true);
        // nothing changed → no email
        saleService.update(sale.id(), reqTotal("INV-A1", "amt@example.com", 350000, 50000), ADMIN, true);
        // amounts changed but notify off → no email
        saleService.update(sale.id(), reqTotal("INV-A1", "amt@example.com", 360000, 50000), ADMIN, false);

        assertThat(notificationService.forSale(sale.id()).reversed()).extracting(NotificationResponse::type)
                .containsExactly(NotificationType.INVOICE_UPDATED, NotificationType.INVOICE_UPDATED);
        EmailMessage updated = mail.sent.getFirst();
        assertThat(updated.subject()).isEqualTo("Updated invoice INV-A1 from Test Solar Co");
        assertThat(updated.html()).contains("Your invoice has been updated", "Billed to", "₹3,50,000", "₹2,50,000");
        assertThat(mail.sent.getLast().text()).contains("Paid: ₹50,000", "Balance due: ₹3,00,000");
    }

    @Test
    void failedSendIsRecordedAndCanBeResent() {
        mail.fail = true;
        SaleResponse sale = saleService.create(req("INV-F1", "fail@example.com", InstallationStatus.PENDING, null, 0), ADMIN, true);
        NotificationResponse failed = notificationService.forSale(sale.id()).getFirst();
        assertThat(failed.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.detail()).isEqualTo("SMTP server unavailable");

        mail.fail = false;
        NotificationResponse retried = notificationService.resend(failed.id(), "Anita Admin");

        assertThat(retried.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(retried.type()).isEqualTo(NotificationType.INVOICE);
        assertThat(mail.sent).hasSize(1);
    }

    @Test
    void rolledBackChangeNeverEmailsTheCustomer() {
        new TransactionTemplate(txManager).executeWithoutResult(tx -> {
            saleService.create(req("INV-RB", "rb@example.com", InstallationStatus.PENDING, null, 0), ADMIN, true);
            tx.setRollbackOnly();
        });

        assertThat(notifications.count()).isZero();
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void excelImportEmailsInvoicesOnlyWhenAsked() throws Exception {
        saleService.importExcel(xlsx("INV-X1", "x1@example.com", "INV-X2", "x2@example.com"), false, ADMIN, false);
        assertThat(notifications.count()).isZero();

        saleService.importExcel(xlsx("INV-X3", "x3@example.com", "INV-X4", null), false, ADMIN, true);
        assertThat(notifications.findAll()).extracting(n -> n.getInvoiceNumber() + ":" + n.getStatus())
                .containsExactlyInAnyOrder("INV-X3:SENT", "INV-X4:SKIPPED");
    }

    @Test
    void notificationEndpointsAreAdminOnly() throws Exception {
        AppUser admin = users.save(new AppUser("Anita Admin", "boss@test.local", Role.ADMIN));
        AppUser rep = users.save(new AppUser("Rep", "rep@test.local", Role.USER));
        SaleResponse sale = saleService.create(req("INV-E1", "e1@example.com", InstallationStatus.PENDING, null, 0), ADMIN, false);

        mvc.perform(post("/api/sales/" + sale.id() + "/notifications/invoice").with(as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.typeLabel").value("Invoice"));
        mvc.perform(get("/api/sales/" + sale.id() + "/notifications").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipient").value("e1@example.com"));
        mvc.perform(get("/api/notifications").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post("/api/sales/" + sale.id() + "/notifications/invoice").with(as(rep)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/" + sale.id() + "/notifications").with(as(rep))).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications").with(as(rep))).andExpect(status().isForbidden());
    }

    @Test
    void sendingForAMissingSaleIs404() throws Exception {
        AppUser admin = users.save(new AppUser("Anita Admin", "boss@test.local", Role.ADMIN));
        mvc.perform(post("/api/sales/999999/notifications/invoice").with(as(admin)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private static SaleRequest req(String invoice, String email, InstallationStatus status, LocalDate installDate, long paid) {
        return new SaleRequest(invoice, LocalDate.now().minusDays(10), nameFor(email),
                "9876543210", email, "Pune", "Maharashtra", "Monocrystalline", new BigDecimal("5"), 10, "Luminous",
                new BigDecimal("300000"), BigDecimal.valueOf(paid), status, installDate, "Anita");
    }

    private static SaleRequest reqTotal(String invoice, String email, long total, long paid) {
        return new SaleRequest(invoice, LocalDate.now().minusDays(10), nameFor(email), "9876543210", email, "Pune",
                "Maharashtra", "Monocrystalline", new BigDecimal("5"), 10, "Luminous", BigDecimal.valueOf(total),
                BigDecimal.valueOf(paid), InstallationStatus.PENDING, null, "Anita");
    }

    private static String nameFor(String email) {
        return email == null ? "Walk-in Customer" : Character.toUpperCase(email.charAt(0)) + email.substring(1, email.indexOf('@')) + " Kumar";
    }

    private static RequestPostProcessor as(AppUser u) {
        return oauth2Login().oauth2User(new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole()));
    }

    private static MockMultipartFile xlsx(String inv1, String email1, String inv2, String email2) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            String[] header = {"Invoice No", "Purchase Date", "Customer Name", "Email", "Capacity (kW)", "Total Amount"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            Object[][] rows = {{inv1, "2026-01-15", "First Customer", email1, 3, 180000},
                    {inv2, "2026-01-16", "Second Customer", email2, 4, 240000}};
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (v != null) {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "import.xlsx", "application/octet-stream", out.toByteArray());
        }
    }
}
