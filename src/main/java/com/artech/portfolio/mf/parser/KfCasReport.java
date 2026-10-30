package com.artech.portfolio.mf.parser;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed KFINTECH Consolidated Account Statement - the in-memory twin of the
 * JSON the report converts to (MF.md 4/5). Every field name mirrors that JSON
 * (Jackson annotations supply the PAN / rta_code / statement_period spellings),
 * so the "download parsed JSON" feature (MF.md 16) can serialize this object
 * as-is for a high-level check before reconciliation.
 *
 * Numbers keep their printed text ("1,999.90" -> "1999.90", "(60,000.00)" ->
 * "-60000.00") so nothing is lost between the report and the screen; the
 * staging tables store them as numbers for sums and reports.
 */
public class KfCasReport {

    @JsonProperty("statement_period")
    public StatementPeriod statementPeriod = new StatementPeriod();

    public List<Folio> folios = new ArrayList<>();

    @JsonProperty("investor_info")
    public InvestorInfo investorInfo = new InvestorInfo();

    /** PORTFOLIO SUMMARY block printed on page 1 (MF.md 8/11). */
    public List<SummaryRow> summary = new ArrayList<>();

    @JsonProperty("cas_type")
    public String casType = "DETAILED";

    @JsonProperty("file_type")
    public String fileType = "KFINTECH";

    @JsonProperty("parse_warnings")
    public List<String> parseWarnings = new ArrayList<>();

    /** Report period exactly as printed: "25-Jan-2007" / "25-Oct-2026". */
    public static class StatementPeriod {
        @JsonProperty("from")
        public String from;
        @JsonProperty("to")
        public String to;
    }

    /** One folio of the investor, grouping every scheme held under it. */
    public static class Folio {
        public String folio;
        public String amc;
        public String name;
        @JsonProperty("PAN")
        public String pan;
        @JsonProperty("KYC")
        public String kyc;
        @JsonProperty("PANKYC")
        public String panKyc;
        public List<Scheme> schemes = new ArrayList<>();
    }

    /** One scheme / folio holding with its balances, nominees and ledger. */
    public static class Scheme {
        public String scheme;
        public String advisor;
        @JsonProperty("rta_code")
        public String rtaCode;
        public String rta;
        /** Fund type is not printed on the statement - left null. */
        public String type;
        public String isin;
        /** AMFI code is not printed on the statement - left null. */
        public String amfi;
        public List<String> nominees = new ArrayList<>();
        public String open;
        public String close;
        @JsonProperty("close_calculated")
        public String closeCalculated;
        public Valuation valuation = new Valuation();
        public List<Txn> transactions = new ArrayList<>();
    }

    /** Latest valuation printed under the scheme's closing balance. */
    public static class Valuation {
        public String date;
        public String nav;
        public String cost;
        public String value;
    }

    /** One row of the scheme's transaction ledger. */
    public static class Txn {
        public String date;
        public String description;
        public String amount;
        public String units;
        public String nav;
        public String balance;
        public String type;
        @JsonProperty("dividend_rate")
        public String dividendRate;
        @JsonProperty("gift_folio")
        public String giftFolio;
    }

    /** Investor block printed in the page-1 header. */
    public static class InvestorInfo {
        public String name;
        public String email;
        public String address;
        public String mobile;
    }

    /** One PORTFOLIO SUMMARY line (amc -> cost / market value). */
    public static class SummaryRow {
        public String amc;
        public String cost;
        public String market;
        /** true for the printed "Total" line. */
        public boolean total;
    }
}
