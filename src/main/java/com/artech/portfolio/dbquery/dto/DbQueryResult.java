package com.artech.portfolio.dbquery.dto;

import lombok.Data;

import java.util.List;

/**
 * ADMIN_V1 execution result. DQL returns the complete result set (column
 * names + rows) so the screen can build the CSV with the headers on top;
 * DML/DDL/TCL only report the update count. On failure the controller turns
 * the error into {message: ...} with HTTP 400 so the screen can show it.
 */
@Data
public class DbQueryResult {

    /** Always "Success" - failures are raised as errors, never as a result. */
    private String status = "Success";

    /** The category the query ran under (DQL / DML / DDL / TCL). */
    private String category;

    /** DQL only: result-set column names - become the CSV header row. */
    private List<String> columns;

    /** DQL only: all returned data rows, below the header in the CSV. */
    private List<List<String>> rows;

    /** DQL only: number of data rows returned (CSV rows minus the header). */
    private Integer rowCount;

    /** DQL only: true when the result hit the configured maximum row cap. */
    private Boolean truncated;

    /** DML/DDL/TCL: how many rows the statements changed (-1 when unknown). */
    private Integer rowsAffected;

    /**
     * MFR_V13: how many statements the submitted script held. The screen needs
     * it to say "N statement(s) executed" and to explain a partial result.
     */
    private Integer statementCount;

    /**
     * MFR_V13: what each statement did, in execution order - the screen lists
     * the keyword, the SQL and the rows it changed.
     */
    private List<DbQueryStatementResult> statements;
}
