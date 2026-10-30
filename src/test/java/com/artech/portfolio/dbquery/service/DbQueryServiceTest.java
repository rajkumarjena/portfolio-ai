package com.artech.portfolio.dbquery.service;

import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.auth.service.CurrentUserService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.artech.portfolio.dbquery.dto.DbQueryRequest;
import com.artech.portfolio.dbquery.dto.DbQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADMIN_V1 Database Query rules:
 *   - only an ADMIN can execute (SecurityConfig enforces the endpoint too),
 *   - a SQL category must be selected and the query must not be empty,
 *   - the statement must belong to the selected category,
 *   - successful DQL returns the complete result set (column names + rows)
 *     for the CSV download; DML/DDL/TCL report rowsAffected, no result set,
 *   - a failing statement surfaces the actual database error,
 *   - the configured maximum result size caps DQL and flags truncation.
 */
class DbQueryServiceTest {

    @TempDir
    Path tempDir;

    private CurrentUserService currentUserService;
    private SQLiteDataSource dataSource;
    private DbQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("query-test.db"));

        currentUserService = mock(CurrentUserService.class);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(currentUserService.currentPan()).thenReturn("TESTADMIN1");

        // maxRows=100, timeout=30s, maxSqlLength=100000
        service = new DbQueryService(dataSource, currentUserService, 100, 30, 100_000);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("INSERT INTO sample (id, name) VALUES (1, 'Alpha'), (2, 'Beta')");
        }
    }

    private DbQueryRequest request(String category, String sql) {
        DbQueryRequest request = new DbQueryRequest();
        request.setCategory(category);
        request.setSql(sql);
        return request;
    }

    private AuthException expectRejected(DbQueryRequest request) {
        AuthException error = assertThrows(AuthException.class, () -> service.execute(request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        return error;
    }

    // ------------------------------------------------------------ validation

    @Test
    void rejectsMissingCategory() {
        AuthException error = expectRejected(request(null, "SELECT * FROM sample"));
        assertTrue(error.getMessage().contains("Select a SQL category"), error.getMessage());
    }

    @Test
    void rejectsUnknownCategory() {
        AuthException error = expectRejected(request("  dqlx  ", "SELECT * FROM sample"));
        assertTrue(error.getMessage().contains("Select a SQL category"), error.getMessage());
    }

    @Test
    void rejectsEmptyQuery() {
        AuthException error = expectRejected(request("DQL", "   \n  "));
        assertTrue(error.getMessage().contains("Enter a SQL query"), error.getMessage());
    }

    @Test
    void rejectsNullQuery() {
        AuthException error = expectRejected(request("DQL", null));
        assertTrue(error.getMessage().contains("Enter a SQL query"), error.getMessage());
    }

    @Test
    void rejectsStatementOutsideSelectedCategory() {
        AuthException error = expectRejected(request("DQL", "DELETE FROM sample"));
        assertTrue(error.getMessage().contains("belongs to DML, not to DQL"), error.getMessage());
        assertTrue(error.getMessage().contains("SELECT, VALUES"), error.getMessage());
    }

    @Test
    void rejectsUnsupportedStatement() {
        AuthException error = expectRejected(request("DQL", "PRAGMA database_list"));
        assertTrue(error.getMessage().contains("'PRAGMA' is not a supported statement"),
                error.getMessage());
    }

    @Test
    void rejectsOversizedQuery() {
        DbQueryService tiny = new DbQueryService(dataSource, currentUserService, 100, 30, 10);
        DbQueryRequest request = request("DQL", "SELECT id FROM sample");
        AuthException error = assertThrows(AuthException.class, () -> tiny.execute(request));
        assertTrue(error.getMessage().contains("too long"), error.getMessage());
    }

    @Test
    void rejectsNonAdmin() {
        when(currentUserService.isAdmin()).thenReturn(false);
        AuthException error = assertThrows(AuthException.class,
                () -> service.execute(request("DQL", "SELECT * FROM sample")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertTrue(error.getMessage().contains("ADMIN"), error.getMessage());
    }

    // ------------------------------------------------------------------- DQL

    @Test
    void dqlReturnsStatusColumnsAndAllRows() {
        DbQueryResult result = service.execute(request("DQL", "SELECT id, name FROM sample ORDER BY id"));

        assertEquals("Success", result.getStatus());
        assertEquals("DQL", result.getCategory());
        assertEquals(List.of("id", "name"), result.getColumns());
        assertEquals(2, result.getRowCount());
        assertEquals(List.of("1", "Alpha"), result.getRows().get(0));
        assertEquals(List.of("2", "Beta"), result.getRows().get(1));
        assertFalse(result.getTruncated());
        assertNull(result.getRowsAffected());
    }

    @Test
    void dqlSelectWithLeadingCommentStillValidates() {
        DbQueryResult result = service.execute(request("DQL",
                "-- list everyone\n/* second line */\nSELECT name FROM sample ORDER BY id"));
        assertEquals("Success", result.getStatus());
        assertEquals(List.of("name"), result.getColumns());
        assertEquals(List.of("Alpha"), result.getRows().get(0));
    }

    @Test
    void dqlValuesStatementIsSupported() {
        DbQueryResult result = service.execute(request("DQL", "VALUES (1, 'one'), (2, 'two')"));
        assertEquals("Success", result.getStatus());
        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumns().size());
    }

    @Test
    void dqlCapsResultAtConfiguredMaximumAndFlagsTruncation() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (int i = 3; i <= 6; i++) {
                statement.execute("INSERT INTO sample (id, name) VALUES (" + i + ", 'Row" + i + "')");
            }
        } catch (Exception e) {
            fail(e);
        }

        DbQueryService capped = new DbQueryService(dataSource, currentUserService, 2, 30, 100_000);
        DbQueryResult result = capped.execute(request("DQL", "SELECT id FROM sample ORDER BY id"));

        assertEquals(2, result.getRowCount());
        assertTrue(result.getTruncated());
        assertEquals(2, result.getRows().size());
    }

    // ------------------------------------------------- DML / DDL / TCL

    @Test
    void dmlReportsRowsAffectedWithoutResultColumns() {
        DbQueryResult result = service.execute(
                request("DML", "UPDATE sample SET name = 'Alpha Inc' WHERE id = 1"));

        assertEquals("Success", result.getStatus());
        assertEquals("DML", result.getCategory());
        assertEquals(1, result.getRowsAffected());
        assertNull(result.getColumns());
        assertNull(result.getRows());
    }

    @Test
    void ddlCreateTableSucceedsWithoutCsvPayload() {
        DbQueryResult result = service.execute(request("DDL", "CREATE TABLE extra (x INTEGER)"));

        assertEquals("Success", result.getStatus());
        assertEquals("DDL", result.getCategory());
        assertNull(result.getColumns());
    }

    @Test
    void tclBeginSucceedsWithoutCsvPayload() {
        DbQueryResult result = service.execute(request("TCL", "BEGIN"));

        assertEquals("Success", result.getStatus());
        assertEquals("TCL", result.getCategory());
        assertNull(result.getColumns());
    }

    // ------------------------------------------------------- failure display

    @Test
    void failedQuerySurfacesActualDatabaseError() {
        AuthException error = expectRejected(request("DQL", "SELECT * FROM does_not_exist"));
        assertTrue(error.getMessage().contains("no such table"), error.getMessage());
        assertTrue(error.getMessage().startsWith("Query failed:"), error.getMessage());
    }

    @Test
    void failedDmlSurfacesActualDatabaseError() {
        AuthException error = expectRejected(
                request("DML", "INSERT INTO sample (id, name) VALUES (1, 'Dup')"));
        assertTrue(error.getMessage().contains("UNIQUE constraint failed"),
                error.getMessage());
    }

    @Test
    void failedQueryLeavesConnectionUsableForNextExecution() {
        expectRejected(request("DQL", "SELECT * FROM does_not_exist"));
        DbQueryResult result = service.execute(request("DQL", "SELECT COUNT(*) AS n FROM sample"));
        assertEquals("Success", result.getStatus());
        assertEquals("2", result.getRows().get(0).get(0));
    }

    // ---------------------------------------- MFR_V13: multi-statement script

    @Test
    void everyDmlStatementOfTheScriptRunsNotJustTheFirst() {
        DbQueryResult result = service.execute(request("DML",
                "DELETE FROM sample WHERE id = 1;\n"
                        + "DELETE FROM sample WHERE id = 2;\n"));

        assertEquals(2, result.getStatementCount());
        assertEquals(2, result.getStatements().size());
        assertEquals(2, result.getRowsAffected(), "the counts of all statements are summed");
        assertEquals(1, result.getStatements().get(0).getRowsAffected());
        assertEquals(1, result.getStatements().get(1).getRowsAffected());
        assertEquals("0", scalar("SELECT COUNT(*) FROM sample"),
                "both DELETE statements must have removed their rows");
    }

    @Test
    void everyDdlStatementOfTheScriptRuns() {
        DbQueryResult result = service.execute(request("DDL",
                "CREATE TABLE one (x INTEGER);\nCREATE TABLE two (y TEXT);"));

        assertEquals(2, result.getStatementCount());
        assertEquals(2, result.getStatements().size());
        assertEquals("2", scalar("SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type = 'table' AND name IN ('one', 'two')"));
    }

    @Test
    void aFailingStatementRollsBackTheWholeScript() {
        AuthException error = expectRejected(request("DML",
                "DELETE FROM sample;\nDELETE FROM does_not_exist;"));

        assertTrue(error.getMessage().contains("statement 2 of 2"), error.getMessage());
        assertTrue(error.getMessage().contains("nothing was applied"), error.getMessage());
        assertTrue(error.getMessage().contains("no such table"), error.getMessage());
        assertEquals("2", scalar("SELECT COUNT(*) FROM sample"),
                "the successful DELETE of statement 1 must be rolled back too");
    }

    /**
     * Regression: Connection.setAutoCommit(false) on the pooled proxy does NOT
     * open a SQLite transaction - the DELETE stayed committed while the
     * rollback failed with "cannot rollback - no transaction is active". The
     * script must roll back through the pool the application really uses.
     */
    @Test
    void aFailingStatementRollsBackThroughAPooledConnection() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("pooled.db"));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        try (HikariDataSource pooled = new HikariDataSource(config)) {
            try (Connection connection = pooled.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE guarded (x INTEGER)");
                statement.execute("INSERT INTO guarded (x) VALUES (1)");
            }

            DbQueryService pooledService =
                    new DbQueryService(pooled, currentUserService, 100, 30, 100_000);
            AuthException error = assertThrows(AuthException.class, () -> pooledService.execute(
                    request("DML", "DELETE FROM guarded;\nDELETE FROM does_not_exist;")));
            assertTrue(error.getMessage().contains("rolled back"), error.getMessage());

            DbQueryResult after = pooledService.execute(
                    request("DQL", "SELECT COUNT(*) AS n FROM guarded"));
            assertEquals("1", after.getRows().get(0).get(0),
                    "the DELETE of statement 1 must be rolled back through the pool");
        }
    }

    @Test
    void semicolonInsideAStringLiteralDoesNotSplitTheStatement() {
        DbQueryResult insert = service.execute(request("DML",
                "INSERT INTO sample (id, name) VALUES (3, 'a;b');"));
        assertEquals(1, insert.getStatementCount(), "the ';' is data, not a separator");

        assertEquals("a;b", scalar("SELECT name FROM sample WHERE id = 3"));
    }

    @Test
    void semicolonsInsideCommentsDoNotSplitTheStatement() {
        DbQueryResult result = service.execute(request("DML",
                "-- cleanup; step 1\nDELETE FROM sample WHERE id = 2; -- finished;\n"));

        assertEquals(1, result.getStatementCount());
        assertEquals(1, result.getRowsAffected());
        assertEquals("1", scalar("SELECT COUNT(*) FROM sample"));
    }

    @Test
    void aStatementOutsideTheCategoryIsRejectedEvenWhenItIsNotTheFirst() {
        AuthException error = expectRejected(request("DML",
                "DELETE FROM sample;\nDROP TABLE sample;"));

        assertTrue(error.getMessage().contains("Statement 2 of 2"), error.getMessage());
        assertTrue(error.getMessage().contains("'DROP' belongs to DDL, not to DML"),
                error.getMessage());
        assertEquals("2", scalar("SELECT COUNT(*) FROM sample"), "nothing may run");
    }

    @Test
    void dqlScriptReturnsTheFirstResultSet() {
        DbQueryResult result = service.execute(request("DQL",
                "SELECT id, name FROM sample ORDER BY id;\nSELECT 99 AS other;"));

        assertEquals(2, result.getStatementCount());
        assertEquals(List.of("id", "name"), result.getColumns());
        assertEquals(2, result.getRowCount());
    }

    @Test
    void vacuumScriptStillRunsOutsideATransaction() {
        // SQLite refuses VACUUM inside a transaction: the DDL script must not be wrapped.
        DbQueryResult result = service.execute(request("DDL",
                "CREATE TABLE vacuumed (x INTEGER);\nVACUUM;"));

        assertEquals(2, result.getStatementCount());
        assertEquals("1", scalar("SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type = 'table' AND name = 'vacuumed'"));
    }

    /** First column of the first row of a query (test helper). */
    private String scalar(String sql) {
        DbQueryResult result = service.execute(request("DQL", sql));
        return result.getRows().isEmpty() ? null : result.getRows().get(0).get(0);
    }

    /**
     * Regression: a raw TCL BEGIN opens a SQLite transaction while the driver
     * still reports autoCommit=true. The connection must be cleaned before it
     * returns to the pool, otherwise the next JPA transaction on that pooled
     * connection fails with "cannot start a transaction within a transaction"
     * (observed as an HTTP 500 right after the E2E ran a TCL BEGIN).
     */
    @Test
    void tclBeginDoesNotPoisonTheReturnedConnection() throws Exception {
        try (Connection shared = dataSource.getConnection()) {
            DbQueryService pooledLike = new DbQueryService(
                    singleConnectionDataSource(shared), currentUserService, 100, 30, 100_000);

            DbQueryResult begin = pooledLike.execute(request("TCL", "BEGIN"));
            assertEquals("Success", begin.getStatus());

            // Exactly what JPA does on its next transactional request:
            assertDoesNotThrow(() -> {
                try {
                    shared.setAutoCommit(false); // BEGIN would fail here if the raw BEGIN leaked
                    shared.setAutoCommit(true);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    /** A data source that always hands out one physical connection (pool-like reuse). */
    private DataSource singleConnectionDataSource(Connection shared) {
        Connection reused = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) return null; // never return it
                    return method.invoke(shared, args);
                });
        return new DataSource() {
            @Override
            public Connection getConnection() { return reused; }

            @Override
            public Connection getConnection(String username, String password) { return reused; }

            @Override
            public java.io.PrintWriter getLogWriter() { return null; }

            @Override
            public void setLogWriter(java.io.PrintWriter out) { }

            @Override
            public void setLoginTimeout(int seconds) { }

            @Override
            public int getLoginTimeout() { return 0; }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getAnonymousLogger();
            }

            @Override
            public <T> T unwrap(Class<T> iface) { return iface.cast(this); }

            @Override
            public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
        };
    }
}
