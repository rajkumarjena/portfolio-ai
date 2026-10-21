package com.artech.portfolio.dbquery.dto;

import lombok.Data;

/**
 * ADMIN_V1 execute payload: the selected SQL category (DQL / DML / DDL / TCL)
 * and the SQL text typed on the Database Query screen.
 */
@Data
public class DbQueryRequest {
    /** Mandatory: DQL, DML, DDL or TCL. */
    private String category;

    /** Mandatory: the SQL statement to execute. */
    private String sql;
}
