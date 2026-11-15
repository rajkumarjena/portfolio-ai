package com.artech.portfolio.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BOND_V12 #3: the startup migration moves every Bond SIP instalment row's
 * Investment Date onto its own incremental SIP Start Date, once only - a later
 * manual edit of that date survives the next restart.
 */
class BondV12MigrationTest {

    @Test
    void backfillsIncrementalSipStartDateOnce(@TempDir Path tempDir) throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + tempDir.resolve("bond-v12.db"));
        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE bond_sip (id INTEGER PRIMARY KEY, investment_date INTEGER, sip_date INTEGER)");
            s.execute("CREATE TABLE bond (id INTEGER PRIMARY KEY, bond_sip_id INTEGER, "
                    + "investment_type TEXT, status TEXT, investment_date INTEGER, "
                    + "interest_payment_start_date INTEGER)");
            // SIP instalment: Investment Date still repeats the SIP's investment date (100).
            s.execute("INSERT INTO bond(id, bond_sip_id, investment_type, status, investment_date, "
                    + "interest_payment_start_date) VALUES (1, 7, 'SIP Investment', 'EXECUTED', 100, 200)");
            // One time investment: never touched.
            s.execute("INSERT INTO bond(id, bond_sip_id, investment_type, status, investment_date) "
                    + "VALUES (2, NULL, 'One Time Investment', 'EXECUTED', 300)");
        }

        BondV9Migration migration = new BondV9Migration(source);
        migration.migrate();

        assertEquals(200L, scalar(source, "SELECT investment_date FROM bond WHERE id = 1"),
                "BOND_V12 #3: the instalment carries its own SIP Start Date");
        assertEquals(300L, scalar(source, "SELECT investment_date FROM bond WHERE id = 2"),
                "one time rows stay as-is");

        // A manual edit afterwards must survive the next restart (one-shot marker).
        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE bond SET investment_date = 999 WHERE id = 1");
        }
        migration.migrate();
        assertEquals(999L, scalar(source, "SELECT investment_date FROM bond WHERE id = 1"));
    }

    private long scalar(DriverManagerDataSource source, String sql) throws Exception {
        try (Connection c = source.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }
}
