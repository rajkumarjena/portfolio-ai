package com.artech.portfolio.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * FD_V1 #1: 'tenure' is removed from the Fixed Deposit screens. The deposit
 * and maturity DATES already define the tenure, so the redundant
 * fixed_deposit.tenure_months column is dropped here.
 *
 * Hibernate's ddl-auto=update never removes a column, so this startup
 * migration does it. Every statement is guarded, so running it on every
 * restart is safe.
 */
@Component
public class FdV1Migration {

    private static final Logger log = LoggerFactory.getLogger(FdV1Migration.class);

    private final DataSource dataSource;

    public FdV1Migration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Runs whenever a real web server comes up. */
    @Order(1) // after BondV9Migration
    @EventListener(ServletWebServerInitializedEvent.class)
    public void migrateOnStartup() {
        try {
            migrate();
            log.info("FD_V1 schema migration complete (fixed_deposit.tenure_months dropped)");
        } catch (Exception e) {
            // Never block startup - the log carries the detail for the admin.
            log.error("FD_V1 schema migration failed", e);
        }
    }

    /** Idempotent - safe to call on every application start. */
    void migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (columns(connection, "fixed_deposit").contains("tenure_months")) {
                statement.execute("ALTER TABLE fixed_deposit DROP COLUMN tenure_months");
            }
        }
    }

    /** Column names of one table (empty when the table does not exist yet). */
    private Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        // Table names are compile-time constants above, never user input.
        try (ResultSet rs = connection.createStatement()
                .executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                names.add(rs.getString("name").toLowerCase());
            }
        }
        return names;
    }
}
