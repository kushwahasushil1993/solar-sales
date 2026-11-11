package com.solar.sales.config;

import com.solar.sales.dto.SaleRequest;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.Role;
import com.solar.sales.repository.NotificationRepository;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.SaleService;
import com.solar.sales.support.TestSenders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reproduces a database created before IN_PROGRESS existed and checks that startup repairs it. */
@SpringBootTest
@Import(TestSenders.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // alters the shared schema
class LegacySchemaCleanupTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "boss@test.local", "Admin", Role.ADMIN);

    @Autowired JdbcTemplate jdbc;
    @Autowired LegacySchemaCleanup cleanup;
    @Autowired SaleService saleService;
    @Autowired SolarSaleRepository sales;
    @Autowired NotificationRepository notifications;

    @Test
    void oldEnumColumnIsWidenedSoInProgressCanBeSaved() throws Exception {
        notifications.deleteAll();
        sales.deleteAll();
        // what Hibernate created for databases from the first release: an ENUM without IN_PROGRESS
        cleanup.run(null); // drop the CHECK this fresh test schema has, so the column type can be changed
        jdbc.execute("ALTER TABLE solar_sales ALTER COLUMN installation_status SET DATA TYPE ENUM('INSTALLED','PENDING','SCHEDULED')");
        assertThatThrownBy(() -> saleService.create(sale("INV-OLD1"), ADMIN, false))
                .isInstanceOf(DataIntegrityViolationException.class);

        cleanup.run(null);
        cleanup.run(null); // idempotent

        assertThat(saleService.create(sale("INV-OLD2"), ADMIN, false).installationStatus())
                .isEqualTo(InstallationStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject("select installation_status from solar_sales where invoice_number = 'INV-OLD2'",
                String.class)).isEqualTo("IN_PROGRESS");
        // nothing left that would block a status added in the future
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name
                WHERE tc.table_name = 'SOLAR_SALES' AND UPPER(cc.check_clause) LIKE '%INSTALLATION_STATUS%'""",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'SOLAR_SALES' AND column_name = 'INSTALLATION_STATUS'""", String.class))
                .isEqualTo("CHARACTER VARYING");
    }

    @Test
    void oldNotificationColumnsAcceptNewEmailTypes() {
        cleanup.run(null);
        // the notifications table as created before INVOICE_UPDATED existed
        jdbc.execute("ALTER TABLE notifications ALTER COLUMN \"TYPE\" SET DATA TYPE ENUM('INVOICE','INSTALLATION_SCHEDULED',"
                + "'INSTALLATION_IN_PROGRESS','PAYMENT_RECEIVED','INSTALLATION_COMPLETED')");
        cleanup.run(null);

        assertThat(jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'NOTIFICATIONS' AND column_name = 'TYPE'""", String.class))
                .isEqualTo("CHARACTER VARYING");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name
                WHERE tc.table_name = 'NOTIFICATIONS' AND tc.constraint_type = 'CHECK'""", Integer.class)).isZero();
    }

    @Test
    void oldRoleColumnAcceptsAgents() {
        cleanup.run(null);
        jdbc.execute("ALTER TABLE app_users ALTER COLUMN \"ROLE\" SET DATA TYPE ENUM('ADMIN','USER')");
        cleanup.run(null);

        jdbc.update("INSERT INTO app_users (full_name, email, role, enabled, email_verified, password_version, created_at) "
                + "VALUES ('Agent', 'agent-legacy@test.local', 'AGENT', true, false, 0, CURRENT_TIMESTAMP)");
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE email = 'agent-legacy@test.local'", String.class))
                .isEqualTo("AGENT");
        jdbc.update("DELETE FROM app_users WHERE email = 'agent-legacy@test.local'");
    }

    private static SaleRequest sale(String invoice) {
        return new SaleRequest(invoice, LocalDate.now().minusDays(5), "Old Data", null, null, null, null, null,
                new BigDecimal("3"), null, null, new BigDecimal("150000"), BigDecimal.ZERO,
                InstallationStatus.IN_PROGRESS, LocalDate.now(), null);
    }
}
