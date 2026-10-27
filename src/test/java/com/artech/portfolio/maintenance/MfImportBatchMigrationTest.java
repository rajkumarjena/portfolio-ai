package com.artech.portfolio.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MF_V1: the columns of the pre-MF_V1 MfImportBatch survived in the real
 * database as NOT NULL primitives, and Hibernate's ddl-auto=update never drops
 * a column. The redesigned upload therefore failed with
 * "NOT NULL constraint failed: mf_import_batch.error_count" because its INSERT
 * lists only the columns the new entity maps.
 *
 * <p>This pins the schema that produced that failure (taken from the live
 * table) and checks the migration retires those columns, leaves the mapped
 * ones alone, and makes the new INSERT succeed. Running it twice must change
 * nothing.</p>
 */
class MfImportBatchMigrationTest {

    /** The live table's columns the redesigned import no longer maps. */
    private static final List<String> STALE = List.of(
            "error_count", "exists_count", "holding_count", "mapped_count",
            "new_count", "new_scheme_count", "txn_line_count",
            "rpt_current", "rpt_holdings_count", "rpt_invested", "rpt_profit",
            "rpt_txn_count", "rpt_units", "report_pan", "validation_details");

    /** Columns the new MfImportBatch still maps - they must survive. */
    private static final List<String> KEPT = List.of(
            "id", "pan", "file_name", "file_type", "uploaded_at", "status",
            "report_from", "report_to", "client_name", "valid_status",
            "accepted_at", "parsed_json", "folio_count", "scheme_count",
            "txn_count", "adjusted_count");

    private DriverManagerDataSource dataSource(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        // a FILE, not jdbc:sqlite::memory: - every getConnection() would open a
        // brand new empty database and the migration would see no table.
        dataSource.setUrl(url);
        return dataSource;
    }

    /** The pre-MF_V1 table exactly as Hibernate created it. */
    private void createLegacySchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE mf_import_batch ("
                    + "id integer primary key autoincrement,"
                    + "accepted_at timestamp(6), client_name varchar(255),"
                    + "error_count integer NOT NULL, exists_count integer NOT NULL,"
                    + "file_name varchar(255), file_type varchar(255),"
                    + "holding_count integer NOT NULL, mapped_count integer NOT NULL,"
                    + "new_count integer NOT NULL, new_scheme_count integer NOT NULL,"
                    + "pan varchar(255), report_from date, report_pan varchar(255),"
                    + "report_to date, rpt_current float(53) NOT NULL,"
                    + "rpt_holdings_count integer NOT NULL, rpt_invested float(53) NOT NULL,"
                    + "rpt_profit float(53) NOT NULL, rpt_txn_count integer NOT NULL,"
                    + "rpt_units float(53) NOT NULL, status varchar(255),"
                    + "txn_line_count integer NOT NULL, uploaded_at timestamp(6),"
                    + "valid_status varchar(255), validation_details varchar(8000),"
                    // the columns MF_V1 added - Hibernate has already added these
                    + "adjusted_count integer, folio_count integer,"
                    + "parsed_json varchar(4000000), scheme_count integer, txn_count integer)");
        }
    }

    private Set<String> columns(Connection connection) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = connection.createStatement().executeQuery(
                "PRAGMA table_info(mf_import_batch)")) {
            while (rs.next()) {
                names.add(rs.getString("name").toLowerCase());
            }
        }
        return names;
    }

    @Test
    void dropsTheStaleColumnsAndLetsTheNewInsertThrough(@TempDir Path tempDir) throws Exception {
        DriverManagerDataSource dataSource = dataSource("jdbc:sqlite:" + tempDir.resolve("mf.db"));
        // one connection for the whole test - DriverManagerDataSource opens a
        // new one per call and a leaked one keeps the file locked on Windows.
        try (Connection connection = dataSource.getConnection()) {
            createLegacySchema(connection);

            // the failure the user hit: the new INSERT omits error_count ...
            Exception before = null;
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO mf_import_batch "
                        + "(accepted_at, adjusted_count, client_name, file_name, file_type,"
                        + " folio_count, pan, parsed_json, report_from, report_to, scheme_count,"
                        + " status, txn_count, uploaded_at, valid_status) "
                        + "VALUES (NULL, 0, 'N', 'f.pdf', 'KFINTECH', 1, 'P', '{}',"
                        + " NULL, NULL, 1, 'UPLOADED', 1, NULL, 'PENDING')");
            } catch (Exception e) {
                before = e;
            }
            assertTrue(before != null && before.getMessage().contains("error_count"),
                    "the legacy schema must reproduce the reported failure: " + before);

            // ... and the migration clears the way for it
            assertEquals(STALE.size(), new MfImportBatchMigration(dataSource).migrate());

            Set<String> after = columns(connection);
            for (String column : STALE) {
                assertFalse(after.contains(column), "dropped: " + column);
            }
            for (String column : KEPT) {
                assertTrue(after.contains(column), "kept: " + column);
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO mf_import_batch "
                        + "(accepted_at, adjusted_count, client_name, file_name, file_type,"
                        + " folio_count, pan, parsed_json, report_from, report_to, scheme_count,"
                        + " status, txn_count, uploaded_at, valid_status) "
                        + "VALUES (NULL, 0, 'N', 'f.pdf', 'KFINTECH', 1, 'P', '{}',"
                        + " NULL, NULL, 1, 'UPLOADED', 1, NULL, 'PENDING')");
            }
        }
    }

    @Test
    void isIdempotentAndToleratesAMissingTable(@TempDir Path tempDir) throws Exception {
        DriverManagerDataSource dataSource = dataSource("jdbc:sqlite:" + tempDir.resolve("mf.db"));
        // a database that has never imported a report has no table at all
        assertEquals(0, new MfImportBatchMigration(dataSource).migrate());

        try (Connection connection = dataSource.getConnection()) {
            createLegacySchema(connection);
        }
        MfImportBatchMigration migration = new MfImportBatchMigration(dataSource);
        assertEquals(STALE.size(), migration.migrate());
        assertEquals(0, migration.migrate(), "a second run must change nothing");
        try (Connection check = dataSource.getConnection()) {
            assertEquals(KEPT.size(), columns(check).size(), "only the mapped columns remain");
        }
    }

}
