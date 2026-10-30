package com.artech.portfolio.dbquery.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ADMIN_V1 Database Query categories and the statements each one allows:
 *
 *   DQL - Data Query Language : SELECT, VALUES
 *   DML - Data Manipulation   : INSERT, UPDATE, DELETE, REPLACE
 *   DDL - Data Definition     : CREATE, DROP, ALTER, VACUUM
 *   TCL - Transaction Control : BEGIN, COMMIT, ROLLBACK, SAVEPOINT
 *
 * The screen drop-down, the input validation and the service-side check all
 * read from here, so the category list exists in exactly one place.
 */
public class DbQueryConstants {

    public static final String CATEGORY_DQL = "DQL";
    public static final String CATEGORY_DML = "DML";
    public static final String CATEGORY_DDL = "DDL";
    public static final String CATEGORY_TCL = "TCL";

    /** The drop-down list, in screen order. */
    public static final List<String> CATEGORIES = List.of(
            CATEGORY_DQL, CATEGORY_DML, CATEGORY_DDL, CATEGORY_TCL);

    /** First keyword -> the category it belongs to (the query validation rule). */
    private static final Map<String, String> STATEMENT_CATEGORY = Map.ofEntries(
            Map.entry("SELECT", CATEGORY_DQL),
            Map.entry("VALUES", CATEGORY_DQL),
            Map.entry("INSERT", CATEGORY_DML),
            Map.entry("UPDATE", CATEGORY_DML),
            Map.entry("DELETE", CATEGORY_DML),
            Map.entry("REPLACE", CATEGORY_DML),
            Map.entry("CREATE", CATEGORY_DDL),
            Map.entry("DROP", CATEGORY_DDL),
            Map.entry("ALTER", CATEGORY_DDL),
            Map.entry("VACUUM", CATEGORY_DDL),
            Map.entry("BEGIN", CATEGORY_TCL),
            Map.entry("COMMIT", CATEGORY_TCL),
            Map.entry("ROLLBACK", CATEGORY_TCL),
            Map.entry("SAVEPOINT", CATEGORY_TCL));

    /** Allowed statements per category, in the order shown to the admin. */
    private static final Map<String, List<String>> CATEGORY_STATEMENTS = Map.of(
            CATEGORY_DQL, List.of("SELECT", "VALUES"),
            CATEGORY_DML, List.of("INSERT", "UPDATE", "DELETE", "REPLACE"),
            CATEGORY_DDL, List.of("CREATE", "DROP", "ALTER", "VACUUM"),
            CATEGORY_TCL, List.of("BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT"));

    /** Human-readable list for the validation error, e.g. "SELECT, VALUES". */
    public static String allowedStatements(String category) {
        return String.join(", ", CATEGORY_STATEMENTS.getOrDefault(category, List.of()));
    }

    /** The category a statement keyword belongs to, or null when unsupported. */
    public static String categoryOfStatement(String keyword) {
        return STATEMENT_CATEGORY.get(keyword);
    }

    /**
     * MFR_V13: the SQL text with leading whitespace and -- / * *...* comments
     * removed, so a chunk that is comment-only can be told apart from a real
     * statement when the admin submits a whole script.
     */
    public static String stripLeadingComments(String sql) {
        String rest = sql == null ? "" : sql;
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
                return rest;
            }
        }
    }

    /** First SQL keyword, skipping leading whitespace and comments ("" when none). */
    public static String firstKeyword(String sql) {
        String rest = stripLeadingComments(sql);
        int end = 0;
        while (end < rest.length() && Character.isLetter(rest.charAt(end))) {
            end++;
        }
        return rest.substring(0, end).toUpperCase(Locale.ROOT);
    }
}
