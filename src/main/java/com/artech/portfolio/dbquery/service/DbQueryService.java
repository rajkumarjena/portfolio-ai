package com.artech.portfolio.dbquery.service;

import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.dbquery.dto.DbQueryRequest;
import com.artech.portfolio.dbquery.dto.DbQueryResult;
import com.artech.portfolio.dbquery.dto.DbQueryStatementResult;
import com.artech.portfolio.dbquery.util.DbQueryConstants;
import com.artech.portfolio.dbquery.util.SqlScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ADMIN_V1 Database Query service: validates and executes the SQL submitted by
 * an ADMIN on the Database Query screen.
 *
 * Development-consideration controls (they do not change the screen's flow):
 *   - authorization: SecurityConfig already requires ROLE_ADMIN for
 *     /api/admin/**, and the service re-checks it before touching the database;
 *   - query validation: category must be selected, SQL must be non-empty and
 *     every statement must start with a keyword the selected category allows;
 *   - execution timeout: every statement runs with a statement timeout;
 *   - maximum result size: DQL returns at most the configured number of rows
 *     (truncated = true tells the screen the result was capped);
 *   - transaction handling: the borrowed connection is always returned to the
 *     pool with auto-commit restored, so a TCL statement cannot leak an open
 *     transaction into the pool;
 *   - audit logging: every execution - who, what, how long, outcome - is
 *     written to the application log (springboot.log, downloadable by an admin).
 *
 * MFR_V13: the text box takes a whole script (many statements separated by ';')
 * - the admin pastes 14 DELETEs at once. A JDBC Statement runs only the FIRST
 * statement of such a text, which is why the screen answered "Success - 0
 * row(s) affected" while the tables kept their data. The script is now split
 * into its statements ({@link SqlScript}), every one of them is run, and the
 * screen gets the outcome of each. DML and DDL run inside ONE transaction -
 * all statements commit together or the first failure rolls the whole script
 * back and is shown on screen with its position.
 */
@Service
public class DbQueryService {

    private static final Logger log = LoggerFactory.getLogger(DbQueryService.class);

    /** Audit trail for direct database access (ADMIN_V1 development considerations). */
    private static final Logger audit = LoggerFactory.getLogger("DBQUERY_AUDIT");

    /** SQLite refuses VACUUM inside a transaction, so such a script runs unwrapped. */
    private static final String VACUUM = "VACUUM";

    private final DataSource dataSource;
    private final CurrentUserService currentUserService;
    private final int maxRows;
    private final int timeoutSeconds;
    private final int maxSqlLength;

    public DbQueryService(DataSource dataSource,
                          CurrentUserService currentUserService,
                          @Value("${app.admin.query.max-rows:10000}") int maxRows,
                          @Value("${app.admin.query.timeout-seconds:30}") int timeoutSeconds,
                          @Value("${app.admin.query.max-sql-length:100000}") int maxSqlLength) {
        this.dataSource = dataSource;
        this.currentUserService = currentUserService;
        this.maxRows = maxRows;
        this.timeoutSeconds = timeoutSeconds;
        this.maxSqlLength = maxSqlLength;
    }

    /**
     * ADMIN_V1: validate the input (category selected, query not empty, every
     * statement matches the category), execute all of it and hand back the
     * result. Successful DQL carries the complete result set (column names +
     * rows) for the CSV download; every category reports what each statement did.
     */
    public DbQueryResult execute(DbQueryRequest request) {
        // Defense in depth: SecurityConfig blocks the endpoint at /api/admin/**,
        // this guard protects the service if it is ever wired elsewhere.
        if (!currentUserService.isAdmin()) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "You need the ADMIN role to run database queries");
        }

        String category = validateCategory(request);
        List<SqlScript.Statement> statements = validateSql(request, category);
        String admin = currentUserService.currentPan();

        long started = System.currentTimeMillis();
        try {
            DbQueryResult result = runSql(category, statements);
            audit.info("admin={} category={} statements={} rows={} ms={} outcome=SUCCESS sql={}",
                    admin, category, statements.size(),
                    result.getRowCount() != null ? result.getRowCount() : result.getRowsAffected(),
                    System.currentTimeMillis() - started, oneLine(request.getSql()));
            return result;
        } catch (StatementFailure failure) {
            audit.warn("admin={} category={} statements={} failed_at={} ms={} outcome=ERROR error={} sql={}",
                    admin, category, statements.size(), failure.statement.index(),
                    System.currentTimeMillis() - started, failure.getReason(), oneLine(failure.statement.sql()));
            // The actual database error goes back to the screen so the admin
            // can correct the query and retry (ADMIN_V1 execution behaviour).
            throw new AuthException(HttpStatus.BAD_REQUEST, failure.getMessage());
        } catch (SQLException e) {
            audit.warn("admin={} category={} ms={} outcome=ERROR error={} sql={}",
                    admin, category, System.currentTimeMillis() - started,
                    e.getMessage(), oneLine(request.getSql()));
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Query failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    // ------------------------------------------------------------ validation

    /** #1 of the flow: a SQL category must be selected. */
    private String validateCategory(DbQueryRequest request) {
        String category = request == null || request.getCategory() == null
                ? "" : request.getCategory().trim().toUpperCase();
        if (!DbQueryConstants.CATEGORIES.contains(category)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Select a SQL category before executing (DQL, DML, DDL or TCL)");
        }
        return category;
    }

    /**
     * #2/#3 of the flow: the script must not be empty, and EVERY statement in
     * it must belong to the selected category - MFR_V13 validates the whole
     * pasted script, not just its first line, so a DROP cannot hide behind a
     * leading DELETE when DML was selected.
     */
    private List<SqlScript.Statement> validateSql(DbQueryRequest request, String category) {
        String script = request.getSql() == null ? "" : request.getSql().trim();
        if (script.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Enter a SQL query to execute");
        }
        if (script.length() > maxSqlLength) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "SQL query is too long (maximum " + maxSqlLength + " characters)");
        }

        List<SqlScript.Statement> statements = SqlScript.split(script);
        if (statements.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "The query contains no SQL statement to execute");
        }

        for (SqlScript.Statement statement : statements) {
            String keyword = statement.keyword();
            String position = statements.size() == 1 ? ""
                    : "Statement " + statement.index() + " of " + statements.size() + ": ";

            if (keyword.isEmpty()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, position
                        + "no SQL statement to execute here - " + statement.preview());
            }
            String actualCategory = DbQueryConstants.categoryOfStatement(keyword);
            if (actualCategory == null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, position
                        + "'" + keyword + "' is not a supported statement. Use one of - DQL: "
                        + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_DQL)
                        + "; DML: " + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_DML)
                        + "; DDL: " + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_DDL)
                        + "; TCL: " + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_TCL));
            }
            if (!category.equals(actualCategory)) {
                throw new AuthException(HttpStatus.BAD_REQUEST, position
                        + "'" + keyword + "' belongs to " + actualCategory + ", not to " + category
                        + ". " + category + " allows: " + DbQueryConstants.allowedStatements(category)
                        + " - select the right category or correct the query");
            }
        }
        return statements;
    }

    // ------------------------------------------------------------ execution

    /** Runs every statement of the script on one connection and maps the outcome. */
    private DbQueryResult runSql(String category, List<SqlScript.Statement> statements)
            throws SQLException {
        DbQueryResult result = new DbQueryResult();
        result.setCategory(category);
        result.setStatementCount(statements.size());
        List<DbQueryStatementResult> outcomes = new ArrayList<>(statements.size());
        result.setStatements(outcomes);

        boolean dql = DbQueryConstants.CATEGORY_DQL.equals(category);
        boolean dml = DbQueryConstants.CATEGORY_DML.equals(category);
        // MFR_V13: DML / DDL commit together - a failing statement must not leave
        // half of the admin's script applied. TCL manages the transaction itself.
        boolean transactional = isTransactional(category, statements);

        Connection connection = dataSource.getConnection();
        try {
            if (transactional) {
                begin(connection);
            }

            boolean anyKnownCount = false;
            int totalChanged = 0;

            for (SqlScript.Statement statement : statements) {
                DbQueryStatementResult outcome = new DbQueryStatementResult();
                outcome.setIndex(statement.index());
                outcome.setKeyword(statement.keyword());
                outcome.setStatement(statement.preview());

                try (Statement jdbc = connection.createStatement()) {
                    jdbc.setQueryTimeout(timeoutSeconds);
                    if (dql) {
                        // One extra row lets us detect a capped result without fetching it.
                        jdbc.setMaxRows(maxRows + 1);
                    }

                    boolean hasResultSet = jdbc.execute(statement.sql());
                    if (dql && hasResultSet) {
                        try (ResultSet rs = jdbc.getResultSet()) {
                            if (result.getColumns() == null) {
                                readResultSet(rs, result);
                            }
                            // A script with several SELECTs still downloads ONE CSV:
                            // the first result set is the one sent to the screen.
                        }
                    } else if (dml) {
                        // Only DML has a meaningful "rows changed" count; the driver
                        // reports a stale number for CREATE / DROP / BEGIN, which
                        // would read as data having been touched.
                        int changed = jdbc.getUpdateCount();
                        if (changed >= 0) {
                            outcome.setRowsAffected(changed);
                            anyKnownCount = true;
                            totalChanged += changed;
                        }
                    }
                } catch (SQLException e) {
                    rollback(connection, transactional);
                    throw new StatementFailure(statement, statements.size(), transactional, e);
                }
                outcomes.add(outcome);
            }

            if (transactional) {
                commit(connection);
            }
            if (DbQueryConstants.CATEGORY_DML.equals(category)) {
                // null when no statement reported a count, so the screen never
                // claims "0 row(s) affected" for a count the driver did not give.
                result.setRowsAffected(anyKnownCount ? totalChanged : null);
            }
            return result;
        } finally {
            // Transaction handling: clean BEFORE the connection goes back to the
            // pool, so a TCL BEGIN can never leak an open transaction into it.
            release(connection);
        }
    }

    /**
     * DML and DDL run as one transaction. SQLite refuses VACUUM inside a
     * transaction, so a DDL script that reaches for it runs statement by
     * statement in auto-commit instead of failing.
     */
    private boolean isTransactional(String category, List<SqlScript.Statement> statements) {
        if (DbQueryConstants.CATEGORY_DML.equals(category)) {
            return true;
        }
        if (!DbQueryConstants.CATEGORY_DDL.equals(category)) {
            return false;
        }
        return statements.stream().noneMatch(s -> VACUUM.equals(s.keyword()));
    }

    /**
     * MFR_V13: the transaction is opened with an explicit SQL BEGIN rather than
     * Connection.setAutoCommit(false). The JDBC pool hands out a proxy whose
     * auto-commit switch does not open a SQLite transaction - a rollback then
     * fails with "cannot rollback - no transaction is active" while the DELETEs
     * stay committed. BEGIN / COMMIT / ROLLBACK as SQL are unambiguous.
     */
    private void begin(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
        }
    }

    private void commit(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("COMMIT");
        }
    }

    private void rollback(Connection connection, boolean transactional) {
        if (!transactional) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException e) {
            log.debug("Could not roll the script back: {}", e.getMessage());
        }
    }

    private void release(Connection connection) {
        // A raw TCL statement (BEGIN / SAVEPOINT) opens a SQLite transaction while
        // the driver still reports autoCommit=true, so its own rollback path never
        // runs. End any such transaction with raw SQL - failing is simply the
        // normal "no transaction is open" case after DQL/DML/DDL or COMMIT.
        try (Statement cleanup = connection.createStatement()) {
            cleanup.execute("ROLLBACK");
        } catch (SQLException e) {
            log.debug("Transaction cleanup: {}", e.getMessage());
        }
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.debug("Could not reset connection to auto-commit: {}", e.getMessage());
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                log.debug("Could not close connection: {}", e.getMessage());
            }
        }
    }

    /** The complete result set: column names (CSV header) + every data row. */
    private void readResultSet(ResultSet rs, DbQueryResult result) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            columns.add(label == null || label.isBlank() ? meta.getColumnName(i) : label);
        }

        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                row.add(value == null ? "" : String.valueOf(value));
            }
            rows.add(row);
        }

        result.setColumns(columns);
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(truncated);
    }

    /** One log-friendly line of the SQL (no multi-line spam in the audit trail). */
    private String oneLine(String sql) {
        String flat = sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }

    /**
     * MFR_V13: a statement failed - carries which one, so the screen shows
     * "statement 5 of 14 ... near ..." and the admin can see exactly where the
     * script stopped and that nothing was half-applied.
     */
    private static final class StatementFailure extends RuntimeException {

        private final transient SqlScript.Statement statement;
        private final String reason;

        private StatementFailure(SqlScript.Statement statement, int total,
                                 boolean transactional, SQLException cause) {
            super(buildMessage(statement, total, transactional, cause));
            this.statement = statement;
            this.reason = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        }

        private String getReason() {
            return reason;
        }

        private static String buildMessage(SqlScript.Statement statement, int total,
                                           boolean transactional, SQLException cause) {
            String where = total == 1
                    ? "Query failed"
                    : "Query failed at statement " + statement.index() + " of " + total
                    + " (" + statement.preview() + ") - "
                    + (transactional
                        ? "the whole script was rolled back, nothing was applied"
                        : "the earlier statements were already applied");
            return where + ": " + (cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }
    }
}
