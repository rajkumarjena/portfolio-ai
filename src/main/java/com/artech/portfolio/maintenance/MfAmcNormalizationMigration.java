package com.artech.portfolio.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** MF_V6: move legacy AMC labels to mf_amc before dropping duplicate name columns. */
@Component
public class MfAmcNormalizationMigration {
    private static final Logger log = LoggerFactory.getLogger(MfAmcNormalizationMigration.class);
    private static final List<TableColumn> AMC_COLUMNS = List.of(
            new TableColumn("mf_folios", "amc"),
            new TableColumn("mf_tmp_folios", "amc"),
            new TableColumn("mf_portfolio_summary", "amc"),
            new TableColumn("mf_tmp_portfolio_summary", "amc"),
            new TableColumn("mf_summary_reconciliation", "amc"));
    private static final List<String> REDUNDANT_SCHEME_COLUMNS = List.of(
            "mf_folio_schemes", "mf_tmp_folio_schemes");

    private final DataSource dataSource;

    public MfAmcNormalizationMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Order(2)
    @EventListener(ServletWebServerInitializedEvent.class)
    public void migrateOnStartup() {
        try {
            int changes = migrate();
            if (changes > 0) log.info("MF_V7 SQLite AMC migration applied {} schema/data change(s)", changes);
        } catch (Exception e) {
            // Keep startup operational, but make a failed migration visible for repair.
            log.error("MF AMC SQLite migration failed; schema changes were rolled back where possible", e);
        }
    }

    /**
     * Idempotent SQLite migration. SQLite requires PRAGMA foreign_keys=OFF
     * before a transaction for table rebuilds; constraints are restored after
     * the transaction on the same pooled connection.
     */
    int migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new SQLException("MF AMC migration requires an idle SQLite connection");
            }
            boolean originalForeignKeys = foreignKeysEnabled(connection);
            setForeignKeys(connection, false);
            try {
                connection.setAutoCommit(false);
                try {
                    int changes = 0;
                    for (TableColumn mapping : AMC_COLUMNS) {
                        Set<String> tableColumns = columns(connection, mapping.table());
                        if (tableColumns.isEmpty()) continue;
                        if (!tableColumns.contains("amc_id")) {
                            execute(connection, "ALTER TABLE " + mapping.table() + " ADD COLUMN amc_id INTEGER");
                            tableColumns.add("amc_id");
                        }
                        if (!tableColumns.contains(mapping.labelColumn())) continue;
                        backfill(connection, mapping, tableColumns);
                        execute(connection, "ALTER TABLE " + mapping.table() + " DROP COLUMN " + mapping.labelColumn());
                        changes++;
                    }
                    backfillFolioFromScheme(connection, "mf_folios", "mf_folio_schemes");
                    backfillFolioFromScheme(connection, "mf_tmp_folios", "mf_tmp_folio_schemes");
                    for (String table : REDUNDANT_SCHEME_COLUMNS) {
                        if (columns(connection, table).contains("amc_id")) {
                            execute(connection, "ALTER TABLE " + table + " DROP COLUMN amc_id");
                            changes++;
                        }
                    }

                    // SQLite has no ALTER TABLE ADD CONSTRAINT. Rebuild only
                    // tables that lack this FK, preserving rows, indexes and triggers.
                    for (TableColumn mapping : AMC_COLUMNS) {
                        if (ensureAmcForeignKey(connection, mapping.table())) changes++;
                    }
                    if (ensureUniqueAmcIndex(connection)) changes++;
                    connection.commit();
                    return changes;
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } finally {
                setForeignKeys(connection, originalForeignKeys);
            }
        }
    }

    private static boolean ensureUniqueAmcIndex(Connection connection) throws SQLException {
        if (columns(connection, "mf_amc").isEmpty()
                || !columns(connection, "mf_amc").contains("normalized_name")) return false;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1 FROM sqlite_master "
                     + "WHERE type='index' AND name='ux_mf_amc_normalized_name'")) {
            if (rs.next()) return false;
        }
        execute(connection, "CREATE UNIQUE INDEX ux_mf_amc_normalized_name ON mf_amc(normalized_name)");
        return true;
    }

    private static boolean ensureAmcForeignKey(Connection connection, String table) throws SQLException {
        Set<String> tableColumns = columns(connection, table);
        if (tableColumns.isEmpty() || !tableColumns.contains("id") || !tableColumns.contains("amc_id")
                || columns(connection, "mf_amc").isEmpty() || hasAmcForeignKey(connection, table)) return false;

        // Preserve referential validity: a dangling legacy id is not a real AMC.
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE " + table + " SET amc_id = NULL WHERE amc_id IS NOT NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM mf_amc a WHERE a.id = " + table + ".amc_id)");
        }

        String createSql = tableCreateSql(connection, table);
        if (createSql == null) return false;
        String temporary = "_mf_v7_new_" + table;
        String staleTemporary = tableCreateSql(connection, temporary);
        if (staleTemporary != null) execute(connection, "DROP TABLE " + quote(temporary));
        int open = createSql.indexOf('(');
        int close = createSql.lastIndexOf(')');
        if (open < 0 || close <= open) throw new SQLException("Unrecognized CREATE TABLE for " + table);
        String newSql = "CREATE TABLE " + quote(temporary) + " "
                + createSql.substring(open, close)
                + ", CONSTRAINT " + quote("fk_" + table + "_amc")
                + " FOREIGN KEY (amc_id) REFERENCES mf_amc(id)"
                + createSql.substring(close);
        List<String> dependentObjects = dependentObjects(connection, table);

        execute(connection, newSql);
        execute(connection, "INSERT INTO " + quote(temporary) + " SELECT * FROM " + quote(table));
        execute(connection, "DROP TABLE " + quote(table));
        execute(connection, "ALTER TABLE " + quote(temporary) + " RENAME TO " + quote(table));
        for (String sql : dependentObjects) execute(connection, sql);
        log.info("Created SQLite AMC foreign key on {}", table);
        return true;
    }

    private static boolean hasAmcForeignKey(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA foreign_key_list(" + quote(table) + ")")) {
            while (rs.next()) {
                if ("mf_amc".equalsIgnoreCase(rs.getString("table"))
                        && "amc_id".equalsIgnoreCase(rs.getString("from"))
                        && "id".equalsIgnoreCase(rs.getString("to"))) return true;
            }
        }
        return false;
    }

    private static String tableCreateSql(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static List<String> dependentObjects(Connection connection, String table) throws SQLException {
        List<String> sqls = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE tbl_name=? AND type IN ('index','trigger') "
                        + "AND sql IS NOT NULL ORDER BY type, name")) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) sqls.add(rs.getString(1));
            }
        }
        return sqls;
    }

    private static boolean foreignKeysEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {
            return rs.next() && rs.getInt(1) != 0;
        }
    }

    private static void setForeignKeys(Connection connection, boolean enabled) throws SQLException {
        execute(connection, "PRAGMA foreign_keys=" + (enabled ? "ON" : "OFF"));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void backfillFolioFromScheme(Connection connection, String folioTable, String schemeTable)
            throws SQLException {
        if (columns(connection, folioTable).isEmpty() || columns(connection, schemeTable).isEmpty()) return;
        Set<String> folioColumns = columns(connection, folioTable);
        Set<String> schemeColumns = columns(connection, schemeTable);
        if (!folioColumns.contains("amc_id") || !schemeColumns.contains("amc_id")) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE " + folioTable + " SET amc_id = (SELECT MIN(s.amc_id) FROM "
                    + schemeTable + " s WHERE s.folio_id = " + folioTable + ".id AND s.amc_id IS NOT NULL "
                    + "GROUP BY s.folio_id HAVING COUNT(DISTINCT s.amc_id) = 1) "
                    + "WHERE amc_id IS NULL AND EXISTS (SELECT 1 FROM " + schemeTable + " s WHERE s.folio_id = "
                    + folioTable + ".id AND s.amc_id IS NOT NULL GROUP BY s.folio_id "
                    + "HAVING COUNT(DISTINCT s.amc_id) = 1)");
        }
    }

    private void backfill(Connection connection, TableColumn mapping, Set<String> columns) throws SQLException {
        String totalColumn = mapping.table().endsWith("portfolio_summary") && columns.contains("is_total")
                ? "is_total" : null;
        String select = "SELECT id, " + mapping.labelColumn() + (totalColumn == null ? "" : ", " + totalColumn)
                + " FROM " + mapping.table() + " WHERE " + mapping.labelColumn() + " IS NOT NULL"
                + (columns.contains("amc_id") ? " AND amc_id IS NULL" : "");
        List<LabelRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(select)) {
            while (rs.next()) {
                String name = rs.getString(2);
                boolean isTotal = totalColumn != null && rs.getBoolean(3);
                if (name != null && !name.isBlank() && !isTotal && !"total".equalsIgnoreCase(name.trim())) {
                    rows.add(new LabelRow(rs.getLong(1), name.trim().replaceAll("\\s+", " ")));
                }
            }
        }
        Map<String, Long> idsByName = new HashMap<>();
        try (PreparedStatement find = connection.prepareStatement(
                     "SELECT id FROM mf_amc WHERE normalized_name = ?");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO mf_amc(name, normalized_name) VALUES (?, ?)");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE " + mapping.table() + " SET amc_id = ? WHERE id = ? AND amc_id IS NULL")) {
            for (LabelRow row : rows) {
                String normalized = normalize(row.name());
                Long amcId = idsByName.get(normalized);
                if (amcId == null) {
                    find.setString(1, normalized);
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next()) amcId = rs.getLong(1);
                    }
                    if (amcId == null) {
                        insert.setString(1, row.name());
                        insert.setString(2, normalized);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (keys.next()) amcId = keys.getLong(1);
                        }
                        if (amcId == null) {
                            find.setString(1, normalized);
                            try (ResultSet rs = find.executeQuery()) {
                                if (rs.next()) amcId = rs.getLong(1);
                            }
                        }
                    }
                    if (amcId == null) throw new SQLException("Could not resolve migrated AMC " + row.name());
                    idsByName.put(normalized, amcId);
                }
                update.setLong(1, amcId);
                update.setLong(2, row.id());
                update.executeUpdate();
            }
        }
    }

    private static Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) result.add(rs.getString("name").toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
                .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private record TableColumn(String table, String labelColumn) {}
    private record LabelRow(long id, String name) {}
}
