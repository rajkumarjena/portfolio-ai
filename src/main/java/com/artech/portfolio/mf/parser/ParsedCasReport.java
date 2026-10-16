package com.artech.portfolio.mf.parser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Format-independent result of parsing one CAS statement (MF.md 14).
 * The reconciliation service turns this into MfImportBatch + MfImportLine rows.
 */
public class ParsedCasReport {

    // Client / statement header printed on top of the report
    public String clientName;
    public String pan;
    public LocalDate reportFrom;
    public LocalDate reportTo;

    // Totals printed on the report itself (header-level validation, MF.md 15)
    public boolean totalsFound;
    public double rptInvested;
    public double rptCurrent;
    public double rptProfit;
    public double rptUnits;
    public boolean unitsTotalFound;
    public int rptHoldingsCount;
    public int rptTxnCount;

    public List<Holding> holdings = new ArrayList<>();
    public List<Txn> transactions = new ArrayList<>();

    /** Report "header detail" row: one scheme / folio holding. */
    public static class Holding {
        public String schemeName;
        public String amcName;
        public String category;
        public String folioNo;
        public double invested;
        public double current;
        public double ret;
        public double units;
    }

    /** Report "detail" row: one transaction of a scheme. */
    public static class Txn {
        public String schemeName;
        public String description;
        public LocalDate date;
        public double nav;
        public double units;
        public double amount;

        /**
         * Folio this transaction belongs to, when the format carries it. The PDF
         * statement prints folio sections, so its rows are attributed exactly; the
         * xlsx / csv transaction sheets have no folio column and leave this null
         * (the service then falls back to scheme-level enrichment).
         */
        public String folioNo;
    }
}
