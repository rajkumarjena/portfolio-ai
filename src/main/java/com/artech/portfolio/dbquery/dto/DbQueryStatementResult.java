package com.artech.portfolio.dbquery.dto;

import lombok.Data;

/**
 * MFR_V13: outcome of one statement of the submitted script. The screen lists
 * these so the admin can see that every DELETE / DROP actually ran and how many
 * rows each one changed, instead of a single aggregate that hid 13 statements.
 */
@Data
public class DbQueryStatementResult {

    /** 1-based position of the statement in the submitted script. */
    private Integer index;

    /** First SQL keyword of the statement (DELETE, UPDATE, DROP, ...). */
    private String keyword;

    /** One flattened line of the statement, for display. */
    private String statement;

    /** Rows changed by this statement (-1 when the statement reports no count). */
    private Integer rowsAffected;
}
