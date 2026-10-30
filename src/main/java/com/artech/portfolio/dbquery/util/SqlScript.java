package com.artech.portfolio.dbquery.util;

import java.util.ArrayList;
import java.util.List;

/**
 * MFR_V13: the Database Query screen accepts a whole script (many statements
 * separated by ';'), but a JDBC <code>Statement</code> only ever runs the first
 * one - which is why a pasted list of DELETEs reported "0 row(s) affected" and
 * left every other table untouched.
 *
 * <p>This splitter cuts the text into its individual statements so the service
 * can run all of them. A ';' inside a string literal, a quoted identifier, a
 * bracket-quoted name, a <code>--</code> line comment or a <code>/* ... *&#47;</code>
 * block comment is NOT a separator; empty chunks (stray semicolons, trailing
 * comments) are dropped.</p>
 */
public final class SqlScript {

    /**
     * One statement of the script: its 1-based position (as the admin sees it),
     * the first SQL keyword ("" when the chunk holds no statement) and the SQL
     * text itself.
     */
    public record Statement(int index, String keyword, String sql) {

        /** One flattened, length-limited line of the statement for messages. */
        public String preview() {
            String flat = sql.replaceAll("\\s+", " ").trim();
            return flat.length() > 120 ? flat.substring(0, 120) + "..." : flat;
        }
    }

    private SqlScript() {
    }

    /** Splits a SQL script into its executable statements, in submitted order. */
    public static List<Statement> split(String script) {
        List<Statement> statements = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return statements;
        }

        StringBuilder chunk = new StringBuilder();
        int i = 0;
        int n = script.length();
        while (i < n) {
            char c = script.charAt(i);

            if (c == '\'') {
                // String literal: '' is an escaped quote, a single ' ends it.
                chunk.append(c);
                i++;
                while (i < n) {
                    char d = script.charAt(i);
                    chunk.append(d);
                    i++;
                    if (d == '\'') {
                        if (i < n && script.charAt(i) == '\'') {
                            chunk.append('\'');
                            i++;
                        } else {
                            break;
                        }
                    }
                }
            } else if (c == '"' || c == '`') {
                // Quoted identifier: a doubled quote is an escaped quote.
                char quote = c;
                chunk.append(c);
                i++;
                while (i < n) {
                    char d = script.charAt(i);
                    chunk.append(d);
                    i++;
                    if (d == quote) {
                        if (i < n && script.charAt(i) == quote) {
                            chunk.append(quote);
                            i++;
                        } else {
                            break;
                        }
                    }
                }
            } else if (c == '[') {
                // [bracketed] identifier, as SQLite / SQL Server accept it.
                chunk.append(c);
                i++;
                while (i < n) {
                    char d = script.charAt(i);
                    chunk.append(d);
                    i++;
                    if (d == ']') {
                        break;
                    }
                }
            } else if (c == '-' && i + 1 < n && script.charAt(i + 1) == '-') {
                int end = script.indexOf('\n', i);
                if (end < 0) {
                    chunk.append(script, i, n);
                    i = n;
                } else {
                    chunk.append(script, i, end + 1);
                    i = end + 1;
                }
            } else if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                int end = script.indexOf("*/", i + 2);
                if (end < 0) {
                    chunk.append(script, i, n);
                    i = n;
                } else {
                    chunk.append(script, i, end + 2);
                    i = end + 2;
                }
            } else if (c == ';') {
                add(statements, chunk);
                i++;
            } else {
                chunk.append(c);
                i++;
            }
        }
        add(statements, chunk);
        return statements;
    }

    /** Keeps a chunk when it still holds SQL after its comments are removed. */
    private static void add(List<Statement> statements, StringBuilder chunk) {
        String text = chunk.toString().trim();
        chunk.setLength(0);
        if (text.isEmpty() || DbQueryConstants.stripLeadingComments(text).isEmpty()) {
            return;
        }
        statements.add(new Statement(statements.size() + 1,
                DbQueryConstants.firstKeyword(text), text));
    }
}
