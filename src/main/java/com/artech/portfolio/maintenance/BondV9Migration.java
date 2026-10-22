package com.artech.portfolio.maintenance;

import com.artech.portfolio.bond.util.BondConstants;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BONDBP_V9 #3/#12/#13 - one idempotent, self-healing startup migration that
 * keeps the SQLite schema in line with the merged Bond SIP process:
 *
 *   1. bond_sip.create_date  -> investment_date   (#3: screen + column rename)
 *      and drop the removed actual_sip_amount mirror column;
 *   2. backfill the new bond lifecycle columns for pre-V9 rows:
 *      investment_type = "One Time Investment", status = "EXECUTED" (#2/#6 -
 *      every record that exists today was a direct one-time entry);
 *   3. drop the removed bond_sip_transaction table (#4/#13).
 *
 * Hibernate's ddl-auto=update has already added the new columns by the time a
 * web server starts (this listener never fires in MockMvc tests), so the data
 * step only has to fill them in. Every statement is guarded, so running it on
 * every restart is safe.
 */
@Component
public class BondV9Migration {

    private static final Logger log = LoggerFactory.getLogger(BondV9Migration.class);

    private final DataSource dataSource;

    public BondV9Migration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Runs first (before the Bond SIP restart sync) whenever a real web server comes up. */
    @Order(0)
    @EventListener(ServletWebServerInitializedEvent.class)
    public void migrateOnStartup() {
        try {
            migrate();
            log.info("BONDBP_V9 schema migration complete (bond_sip rename, bond backfill, "
                    + "bond_sip_transaction dropped)");
        } catch (Exception e) {
            // Never block startup - the log carries the detail for the admin.
            log.error("BONDBP_V9 schema migration failed", e);
        }
    }

    /** Idempotent - safe to call on every application start. */
    void migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // ---- 1. bond_sip: Create Date -> Investment Date, drop old mirror column
            Set<String> sipColumns = columns(connection, "bond_sip");
            if (sipColumns.contains("create_date")) {
                if (!sipColumns.contains("investment_date")) {
                    statement.execute("ALTER TABLE bond_sip RENAME COLUMN create_date "
                            + "TO investment_date");
                } else {
                    statement.executeUpdate("UPDATE bond_sip SET investment_date = create_date "
                            + "WHERE investment_date IS NULL");
                    statement.execute("ALTER TABLE bond_sip DROP COLUMN create_date");
                }
            }
            if (sipColumns.contains("actual_sip_amount")) {
                statement.execute("ALTER TABLE bond_sip DROP COLUMN actual_sip_amount");
            }
            // Hibernate reads SQLite dates as epoch millis, so any date that is
            // still stored as 'yyyy-MM-dd' text is converted here - text dates
            // would otherwise fail with "Error parsing date" on the first read.
            normalizeTextDates(connection, statement, "investment_date");
            normalizeTextDates(connection, statement, "sip_date");

            // ---- 2. bond: fill the new lifecycle columns for pre-V9 rows
            if (columns(connection, "bond").contains("investment_type")) {
                statement.executeUpdate("UPDATE bond SET investment_type = '"
                        + BondConstants.INVESTMENT_TYPE_ONE_TIME + "' "
                        + "WHERE investment_type IS NULL OR investment_type = ''");
                statement.executeUpdate("UPDATE bond SET status = '"
                        + BondConstants.STATUS_EXECUTED + "' "
                        + "WHERE status IS NULL OR status = ''");
            }

            // ---- 3. the Bond SIP Transaction table no longer exists (#4/#13)
            statement.execute("DROP TABLE IF EXISTS bond_sip_transaction");
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

    /**
     * Rewrites 'yyyy-MM-dd' text values of one bond_sip date column as epoch
     * millis (the format Hibernate binds and reads). Idempotent: numeric rows
     * are already in the right format and are left untouched. Column names are
     * compile-time constants, never user input.
     */
    private void normalizeTextDates(Connection connection, Statement statement, String column)
            throws SQLException {
        List<long[]> conversions = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery("SELECT id, " + column
                + " FROM bond_sip WHERE typeof(" + column + ") = 'text'")) {
            while (rs.next()) {
                String raw = rs.getString(2);
                if (raw == null || !raw.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                    continue;
                }
                LocalDate date = LocalDate.parse(raw.substring(0, 10));
                long millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                conversions.add(new long[]{rs.getLong(1), millis});
            }
        }
        if (conversions.isEmpty()) {
            return;
        }
        try (java.sql.PreparedStatement update = connection.prepareStatement(
                "UPDATE bond_sip SET " + column + " = ? WHERE id = ?")) {
            for (long[] conversion : conversions) {
                update.setLong(1, conversion[1]);
                update.setLong(2, conversion[0]);
                update.executeUpdate();
            }
        }
        log.info("BONDBP_V9 migration: converted {} '{}' text date value(s) to epoch millis",
                conversions.size(), column);
    }
}
