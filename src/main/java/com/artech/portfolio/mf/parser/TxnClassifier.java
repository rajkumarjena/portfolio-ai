package com.artech.portfolio.mf.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies a ledger row from its description and signed units, exactly the
 * way the reference conversion of the KFINTECH report does (MF.md 4), so the
 * "type" of every row in the downloaded JSON matches the sample:
 *
 *   dividend / IDCW line      -> DIVIDEND_PAYOUT | DIVIDEND_REINVEST (+rate)
 *   no units (tax/fee lines)  -> STT_TAX | STAMP_DUTY_TAX | TDS_TAX | MISC
 *   units > 0                 -> GIFT_IN | SWITCH_IN(_MERGER) | SEGREGATION
 *                                | PURCHASE_SIP | PURCHASE
 *   units < 0                 -> GIFT_OUT | REVERSAL | SWITCH_OUT(_MERGER)
 *                                | REDEMPTION
 *   units = 0                 -> UNKNOWN
 *
 * Nothing here is specific to one statement: the rules are keyword based and
 * run for any report the parser reads (MF.md 15 - no hard coded values).
 */
public final class TxnClassifier {

    /** Transaction types of the reference report, plus ADJUSTED (MF.md 10). */
    public static final String TYPE_DIVIDEND_PAYOUT = "DIVIDEND_PAYOUT";
    public static final String TYPE_DIVIDEND_REINVEST = "DIVIDEND_REINVEST";
    public static final String TYPE_STT_TAX = "STT_TAX";
    public static final String TYPE_STAMP_DUTY_TAX = "STAMP_DUTY_TAX";
    public static final String TYPE_TDS_TAX = "TDS_TAX";
    public static final String TYPE_MISC = "MISC";
    public static final String TYPE_UNKNOWN = "UNKNOWN";
    public static final String TYPE_GIFT_IN = "GIFT_IN";
    public static final String TYPE_GIFT_OUT = "GIFT_OUT";
    public static final String TYPE_SEGREGATION = "SEGREGATION";
    public static final String TYPE_SWITCH_IN = "SWITCH_IN";
    public static final String TYPE_SWITCH_IN_MERGER = "SWITCH_IN_MERGER";
    public static final String TYPE_SWITCH_OUT = "SWITCH_OUT";
    public static final String TYPE_SWITCH_OUT_MERGER = "SWITCH_OUT_MERGER";
    public static final String TYPE_PURCHASE_SIP = "PURCHASE_SIP";
    public static final String TYPE_PURCHASE = "PURCHASE";
    public static final String TYPE_REDEMPTION = "REDEMPTION";
    public static final String TYPE_REVERSAL = "REVERSAL";
    /** Adjustment row written by the first import (MF.md 10). */
    public static final String TYPE_ADJUSTED = "ADJUSTED";

    // "(div.|dividend|idcw) ... @ Rs. 4.00 per unit" -> per-unit dividend rate
    private static final Pattern DIVIDEND_RATE = Pattern.compile(
            "(?:div\\.|dividend|idcw).*?@\\s*Rs\\.\\s*([\\d.]+)(?:\\s+per\\s+unit)?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REINVEST = Pattern.compile("reinvest", Pattern.CASE_INSENSITIVE);
    private static final Pattern STP = Pattern.compile(
            "\\bs\\s*t\\s*p\\b|systematic\\s+transfer", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTALMENT = Pattern.compile("instal+ment", Pattern.CASE_INSENSITIVE);
    private static final Pattern SYS_INVEST = Pattern.compile(
            "sys.+?invest", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern REVERSAL = Pattern.compile(
            "reversal|rejection|dishonoured|mismatch|insufficient\\s+balance|"
                    + "payment\\s+not\\s+received", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIFT_FOLIO = Pattern.compile(
            "Folio\\s+No\\s*[:.]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private TxnClassifier() {
    }

    /** Result of classification: type, dividend rate, counterparty folio. */
    public record Result(String type, String dividendRate, String giftFolio) {
    }

    /**
     * @param description printed ledger text
     * @param units       parsed units, or null when the row carries no units
     */
    public static Result classify(String description, Double units) {
        String d = description == null ? "" : description;
        String l = d.toLowerCase(Locale.ROOT);

        Matcher div = DIVIDEND_RATE.matcher(l);
        if (div.find()) {
            String rate = div.group(1);
            return new Result(REINVEST.matcher(l).find()
                    ? TYPE_DIVIDEND_REINVEST : TYPE_DIVIDEND_PAYOUT, rate, giftFolio(d));
        }
        if (units == null) {
            String type;
            if (l.contains("stt")) type = TYPE_STT_TAX;
            else if (l.contains("stamp")) type = TYPE_STAMP_DUTY_TAX;
            else if (l.contains("tds")) type = TYPE_TDS_TAX;
            else type = TYPE_MISC;
            return new Result(type, null, giftFolio(d));
        }
        if (units > 0) {
            String type;
            if (l.contains("gift")) type = TYPE_GIFT_IN;
            else if (l.contains("switch") || STP.matcher(l).find()) {
                type = l.contains("merger") ? TYPE_SWITCH_IN_MERGER : TYPE_SWITCH_IN;
            } else if (l.contains("segregat")) type = TYPE_SEGREGATION;
            else if (l.contains("sip") || l.contains("systematic")
                    || INSTALMENT.matcher(l).find() || SYS_INVEST.matcher(l).find()) {
                type = TYPE_PURCHASE_SIP;
            } else type = TYPE_PURCHASE;
            return new Result(type, null, giftFolio(d));
        }
        if (units < 0) {
            String type;
            if (l.contains("gift")) type = TYPE_GIFT_OUT;
            else if (REVERSAL.matcher(l).find()) type = TYPE_REVERSAL;
            else if (l.contains("switch") || STP.matcher(l).find()) {
                type = l.contains("merger") ? TYPE_SWITCH_OUT_MERGER : TYPE_SWITCH_OUT;
            } else type = TYPE_REDEMPTION;
            return new Result(type, null, giftFolio(d));
        }
        return new Result(TYPE_UNKNOWN, null, giftFolio(d));
    }

    /** Counterparty folio of a gift transfer, or null. */
    private static String giftFolio(String description) {
        Matcher m = GIFT_FOLIO.matcher(description);
        return m.find() ? m.group(1) : null;
    }
}
