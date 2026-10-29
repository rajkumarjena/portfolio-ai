package com.artech.portfolio.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfAmcNormalizationMigrationTest {
    @Test
    void backfillsAndNormalizesLegacyAmcColumns(@TempDir Path tempDir) throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + tempDir.resolve("migration.db"));
        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE mf_amc (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(120) NOT NULL, normalized_name VARCHAR(120) NOT NULL UNIQUE)");
            s.execute("CREATE TABLE mf_folios (id INTEGER PRIMARY KEY, amc VARCHAR(120), amc_id INTEGER)");
            s.execute("CREATE TABLE mf_tmp_folios (id INTEGER PRIMARY KEY, amc VARCHAR(120), amc_id INTEGER)");
            s.execute("CREATE TABLE mf_portfolio_summary (id INTEGER PRIMARY KEY, amc VARCHAR(120), amc_id INTEGER, is_total BOOLEAN)");
            s.execute("CREATE TABLE mf_tmp_portfolio_summary (id INTEGER PRIMARY KEY, amc VARCHAR(120), amc_id INTEGER, is_total BOOLEAN)");
            s.execute("CREATE TABLE mf_summary_reconciliation (id INTEGER PRIMARY KEY, amc VARCHAR(120), amc_id INTEGER)");
            s.execute("CREATE TABLE mf_folio_schemes (id INTEGER PRIMARY KEY, folio_id INTEGER, amc_id INTEGER)");
            s.execute("CREATE TABLE mf_tmp_folio_schemes (id INTEGER PRIMARY KEY, folio_id INTEGER, amc_id INTEGER)");
            s.execute("INSERT INTO mf_folios(id, amc) VALUES (1, 'HDFC   Mutual Fund')");
            s.execute("INSERT INTO mf_tmp_folios(id, amc) VALUES (2, 'HDFC Mutual Fund')");
            s.execute("INSERT INTO mf_portfolio_summary(id, amc, is_total) VALUES (3, 'HDFC Mutual Fund', 0), (4, 'Total', 1)");
            s.execute("INSERT INTO mf_tmp_portfolio_summary(id, amc, is_total) VALUES (5, 'HDFC Mutual Fund', 0), (6, 'Total', 1)");
            s.execute("INSERT INTO mf_summary_reconciliation(id, amc) VALUES (7, 'HDFC Mutual Fund')");
            s.execute("INSERT INTO mf_folio_schemes(id, folio_id, amc_id) VALUES (8, 1, 1)");
            s.execute("INSERT INTO mf_tmp_folio_schemes(id, folio_id, amc_id) VALUES (9, 2, 1)");
        }

        MfAmcNormalizationMigration migration = new MfAmcNormalizationMigration(source);
        assertEquals(13, migration.migrate());
        assertEquals(0, migration.migrate());

        try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT count(DISTINCT amc_id) FROM mf_folios UNION ALL SELECT count(*) FROM mf_amc")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            assertEquals(1L, scalar(s, "SELECT amc_id FROM mf_folios WHERE id = 1"));
            assertEquals(1L, scalar(s, "SELECT amc_id FROM mf_tmp_folios WHERE id = 2"));
            assertEquals(1L, scalar(s, "SELECT amc_id FROM mf_portfolio_summary WHERE id = 3"));
            assertEquals(1L, scalar(s, "SELECT amc_id FROM mf_summary_reconciliation WHERE id = 7"));
            assertEquals(1L, scalar(s, "SELECT amc_id IS NULL FROM mf_portfolio_summary WHERE id = 4"));
            assertFalse(columns(c, "mf_folios").contains("amc"));
            assertFalse(columns(c, "mf_portfolio_summary").contains("amc"));
            assertFalse(columns(c, "mf_summary_reconciliation").contains("amc"));
            assertFalse(columns(c, "mf_folio_schemes").contains("amc_id"));
            assertFalse(columns(c, "mf_tmp_folio_schemes").contains("amc_id"));
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='index' "
                    + "AND name='ux_mf_amc_normalized_name'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            for (String table : Set.of("mf_folios", "mf_tmp_folios", "mf_portfolio_summary",
                    "mf_tmp_portfolio_summary", "mf_summary_reconciliation")) {
                assertTrue(hasAmcForeignKey(c, table), "missing AMC foreign key on " + table);
            }
            try (ResultSet rs = s.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(rs.next(), "migration must not leave orphan AMC references");
            }
        }
    }

    private static long scalar(Statement s, String query) throws Exception {
        try (ResultSet rs = s.executeQuery(query)) { return rs.next() ? rs.getLong(1) : -1; }
    }

    private static boolean hasAmcForeignKey(Connection c, String table) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA foreign_key_list(" + table + ")")) {
            while (rs.next()) {
                if ("mf_amc".equalsIgnoreCase(rs.getString("table"))
                        && "amc_id".equalsIgnoreCase(rs.getString("from"))
                        && "id".equalsIgnoreCase(rs.getString("to"))) return true;
            }
            return false;
        }
    }

    private static Set<String> columns(Connection c, String table) throws Exception {
        Set<String> result = new HashSet<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) result.add(rs.getString("name").toLowerCase());
        }
        return result;
    }
}
