package com.artech.portfolio.mf.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The parsed content of a KFINTECH / CAMS "Consolidated Account Summary"
 * statement (MF_V4 2) - the scheme level snapshot "As on" one date, without
 * any ledger: a folio / ISIN / scheme / cost value / units / market value line
 * per holding plus the printed grand totals.
 */
public class MfSummaryReport {

    /** "As on 27-Oct-2026" - the statement runs from inception to this day. */
    public LocalDate asOn;

    /** Email Id printed on the header - checked against the client (MF_V4 2b). */
    public String email;

    /** Investor name printed under the email, when the statement carries it. */
    public String investorName;

    /** One holding line of the summary table. */
    public static class Line {
        public String folio;
        public String isin;
        /** Scheme name as printed, including its fund code prefix. */
        public String scheme;
        public BigDecimal cost;
        public BigDecimal units;
        public LocalDate navDate;
        public BigDecimal price;
        public BigDecimal market;
        /** RTA of the holding ("KFINTECH" / "CAMS"). */
        public String registrar;
    }

    public final List<Line> lines = new ArrayList<>();

    /** The printed "Total" line of the table (MF_V4 2iv). */
    public BigDecimal totalCost;
    public BigDecimal totalMarket;

    /** Anything that could not be read, handed back with the upload result. */
    public final List<String> parseWarnings = new ArrayList<>();

    /** The statement type stored with the import history. */
    public static final String FILE_TYPE = "SUMMARY";
}
