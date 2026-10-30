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
import java.util.List;
import java.util.Set;

/**
 * MF_V1 - one idempotent, self-healing startup migration that retires the
 * columns of the pre-MF_V1 MfImportBatch (MF.md 14/15 of the older import).
 *
 * <p>They were created as NOT NULL primitives by Hibernate, and Hibernate's
 * ddl-auto=update only ever adds columns - it never drops or relaxes one. So a
 * database that already ran the older import kept them, and the redesigned
 * upload - whose INSERT lists only the columns the new entity maps - failed
 * with {@code NOT NULL constraint failed: mf_import_batch.error_count}. Every
 * statement here is guarded, so running it on every restart is safe.</p>
 *
 * <p>Same pattern as {@link BondV9Migration}: it fires when a real web server
 * starts (never in tests), and it never blocks startup.</p>
 */
@Component
public class MfImportBatchMigration {

    private static final Logger log = LoggerFactory.getLogger(MfImportBatchMigration.class);

    /**
     * Columns of the old MfImportBatch the redesigned import no longer carries.
     * Their primitive types made them NOT NULL, which is what rejected the
     * insert; the two string ones are dropped with them for the same reason -
     * nothing reads them any more.
     */
    private static final List<String> OBSOLETE_COLUMNS = List.of(
            "error_count", "exists_count", "holding_count", "mapped_count",
            "new_count", "new_scheme_count", "txn_line_count",
            "rpt_current", "rpt_holdings_count", "rpt_invested", "rpt_profit",
            "rpt_txn_count", "rpt_units",
            "report_pan", "validation_details");

    private final DataSource dataSource;

    public MfImportBatchMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Runs whenever a real web server comes up. */
    @Order(1)
    @EventListener(ServletWebServerInitializedEvent.class)
    public void migrateOnStartup() {
        try {
            int dropped = migrate();
            if (dropped > 0) {
                log.info("MF_V1 schema migration complete ({} obsolete mf_import_batch column(s) dropped)",
                        dropped);
            }
        } catch (Exception e) {
            // Never block startup - the log carries the detail for the admin.
            log.error("MF_V1 schema migration failed", e);
        }
    }

    /** Idempotent - safe to call on every application start. Returns the count dropped. */
    int migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            Set<String> columns = columns(connection, "mf_import_batch");
            if (columns.isEmpty()) {
                return 0; // a database that has never imported a report yet
            }
            int dropped = 0;
            for (String column : OBSOLETE_COLUMNS) {
                if (columns.contains(column)) {
                    // Table and column names are compile-time constants, never user input.
                    statement.execute("ALTER TABLE mf_import_batch DROP COLUMN " + column);
                    dropped++;
                }
            }
            return dropped;
        }
    }

    /** Column names of one table (empty when the table does not exist yet). */
    private Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> names = new java.util.HashSet<>();
        try (ResultSet rs = connection.createStatement()
                .executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                names.add(rs.getString("name").toLowerCase());
            }
        }
        return names;
    }
}
