package com.solar.sales.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Brings databases created by earlier releases in line with the current model (ddl-auto=update never relaxes
 * constraints or changes column types). Every statement is idempotent; works on H2 and PostgreSQL.
 * <ul>
 *   <li>app_users.password_hash / email: were NOT NULL; Google/GitHub-only and mobile accounts have none.</li>
 *   <li>Enum columns (installation status, notification type and status): Hibernate creates them as a database
 *       ENUM type (H2) and/or with a CHECK constraint listing the values that existed at the time, so values added
 *       later (IN_PROGRESS, INVOICE_UPDATED, ...) are rejected. They become plain VARCHAR without a CHECK; the Java
 *       enums still validate values.</li>
 * </ul>
 */
@Component
@Order(0)
public class LegacySchemaCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySchemaCleanup.class);

    private final JdbcTemplate jdbc;

    public LegacySchemaCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String column : new String[]{"password_hash", "email"}) {
            execute("ALTER TABLE app_users ALTER COLUMN " + column + " DROP NOT NULL");
        }
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        widenEnumColumn(database, "solar_sales", "installation_status", 20);
        widenEnumColumn(database, "notifications", "type", 40);
        widenEnumColumn(database, "notifications", "status", 20);
        widenEnumColumn(database, "app_users", "role", 20);          // AGENT, PENDING were added later
        widenEnumColumn(database, "app_users", "requested_role", 20);
        widenEnumColumn(database, "solar_sales", "task_state", 20);
        widenEnumColumn(database, "activities", "action", 30);
        widenEnumColumn(database, "inbox_notifications", "type", 30);
    }

    private void widenEnumColumn(String database, String table, String column, int length) {
        dropCheckConstraints(table, column);
        if ("H2".equalsIgnoreCase(database)) {
            execute("ALTER TABLE " + table + " ALTER COLUMN \"" + column.toUpperCase() + "\" SET DATA TYPE VARCHAR(" + length + ")");
        }
    }

    /** Drops CHECK constraints on {@code table} that mention {@code column} (standard information_schema). */
    private void dropCheckConstraints(String table, String column) {
        try {
            jdbc.queryForList("""
                            SELECT tc.constraint_name FROM information_schema.table_constraints tc
                            JOIN information_schema.check_constraints cc
                              ON cc.constraint_name = tc.constraint_name AND cc.constraint_schema = tc.constraint_schema
                            WHERE UPPER(tc.table_name) = UPPER(?) AND tc.constraint_type = 'CHECK'
                              AND UPPER(cc.check_clause) LIKE ?""",
                    String.class, table, "%" + column.toUpperCase() + "%")
                    .forEach(name -> execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + name + "\""));
        } catch (DataAccessException e) {
            log.warn("Could not look up CHECK constraints on {}.{}: {}", table, column, e.getMostSpecificCause().getMessage());
        }
    }

    private void execute(String sql) {
        try {
            jdbc.execute(sql);
        } catch (DataAccessException e) {
            log.warn("Schema cleanup step failed ({}): {}", sql, e.getMostSpecificCause().getMessage());
        }
    }
}
