package com.artech.portfolio.dbquery.service;

import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.dbquery.dto.DbQueryRequest;
import com.artech.portfolio.dbquery.dto.DbQueryResult;
import com.artech.portfolio.dbquery.util.DbQueryConstants;
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
 * ADMIN_V1 Database Query service: validates and executes one SQL statement
 * submitted by an ADMIN on the Database Query screen.
 *
 * Development-consideration controls (they do not change the screen's flow):
 *   - authorization: SecurityConfig already requires ROLE_ADMIN for
 *     /api/admin/**, and the service re-checks it before touching the database;
 *   - query validation: category must be selected, SQL must be non-empty and
 *     must start with a statement the selected category allows;
 *   - execution timeout: every statement runs with a statement timeout;
 *   - maximum result size: DQL returns at most the configured number of rows
 *     (truncated = true tells the screen the result was capped);
 *   - transaction handling: the borrowed connection is always returned to the
 *     pool with auto-commit restored, so a TCL statement cannot leak an open
 *     transaction into the pool;
 *   - audit logging: every execution - who, what, how long, outcome - is
 *     written to the application log (springboot.log, downloadable by an admin).
 */
@Service
public class DbQueryService {

    private static final Logger log = LoggerFactory.getLogger(DbQueryService.class);

    /** Audit trail for direct database access (ADMIN_V1 development considerations). */
    private static final Logger audit = LoggerFactory.getLogger("DBQUERY_AUDIT");

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
     * ADMIN_V1: validate the input (category selected, query not empty, statement
     * matches the category), execute it and hand back the result. Successful DQL
     * carries the complete result set (column names + rows) for the CSV download.
     */
    public DbQueryResult execute(DbQueryRequest request) {
        // Defense in depth: SecurityConfig blocks the endpoint at /api/admin/**,
        // this guard protects the service if it is ever wired elsewhere.
        if (!currentUserService.isAdmin()) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "You need the ADMIN role to run database queries");
        }

        String category = validateCategory(request);
        String sql = validateSql(request, category);
        String admin = currentUserService.currentPan();

        long started = System.currentTimeMillis();
        try {
            DbQueryResult result = runSql(category, sql);
            audit.info("admin={} category={} rows={} ms={} outcome=SUCCESS sql={}",
                    admin, category,
                    result.getRowCount() != null ? result.getRowCount() : result.getRowsAffected(),
                    System.currentTimeMillis() - started, oneLine(sql));
            return result;
        } catch (SQLException e) {
            audit.warn("admin={} category={} ms={} outcome=ERROR error={} sql={}",
                    admin, category, System.currentTimeMillis() - started,
                    e.getMessage(), oneLine(sql));
            // The actual database error goes back to the screen so the admin
            // can correct the query and retry (ADMIN_V1 execution behaviour).
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
     * #2/#3 of the flow: the query must not be empty, and its first statement
     * must belong to the selected category - e.g. a DELETE is not run under DQL.
     */
    private String validateSql(DbQueryRequest request, String category) {
        String sql = request.getSql() == null ? "" : request.getSql().trim();
        if (sql.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Enter a SQL query to execute");
        }
        if (sql.length() > maxSqlLength) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "SQL query is too long (maximum " + maxSqlLength + " characters)");
        }

        String keyword = firstKeyword(sql);
        if (keyword.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "The query contains no SQL statement to execute");
        }
        String actualCategory = DbQueryConstants.categoryOfStatement(keyword);
        if (actualCategory == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "'" + keyword + "' is not a supported statement. Use one of - DQL: "
                            + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_DQL)
                            + "; DML: " + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_DML)
                            + "; DDL: " + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_DDL)
                            + "; TCL: " + DbQueryConstants.allowedStatements(DbQueryConstants.CATEGORY_TCL));
        }
        if (!category.equals(actualCategory)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "'" + keyword + "' belongs to " + actualCategory + ", not to " + category
                            + ". " + category + " allows: "
                            + DbQueryConstants.allowedStatements(category)
                            + " - select the right category or correct the query");
        }
        return sql;
    }

    /** First SQL keyword, skipping leading whitespace and -- / * *...* comments. */
    private String firstKeyword(String sql) {
        String rest = sql;
        while (true) {
            rest = rest.trim();
            if (rest.startsWith("--")) {
                int nl = rest.indexOf('\n');
                if (nl < 0) return "";
                rest = rest.substring(nl + 1);
            } else if (rest.startsWith("/*")) {
                int end = rest.indexOf("*/");
                if (end < 0) return "";
                rest = rest.substring(end + 2);
            } else {
                break;
            }
        }
        int end = 0;
        while (end < rest.length() && Character.isLetter(rest.charAt(end))) {
            end++;
        }
        return rest.substring(0, end).toUpperCase();
    }

    // ------------------------------------------------------------ execution

    /** Runs one statement on a short-lived connection and maps it to a result. */
    private DbQueryResult runSql(String category, String sql) throws SQLException {
        DbQueryResult result = new DbQueryResult();
        result.setCategory(category);

        Connection connection = dataSource.getConnection();
        try {
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(timeoutSeconds);
                boolean isDql = DbQueryConstants.CATEGORY_DQL.equals(category);
                if (isDql) {
                    // One extra row lets us detect a capped result without fetching it.
                    statement.setMaxRows(maxRows + 1);
                }

                boolean hasResultSet = statement.execute(sql);
                if (isDql && hasResultSet) {
                    readResultSet(statement.getResultSet(), result);
                } else {
                    result.setRowsAffected(statement.getUpdateCount());
                }
            }
            return result;
        } finally {
            // Transaction handling: clean BEFORE the connection goes back to the
            // pool, so a TCL BEGIN can never leak an open transaction into it.
            release(connection);
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
        String flat = sql.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }
}
