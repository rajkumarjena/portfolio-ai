package com.artech.portfolio.mf.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a CAS statement PDF (MF.md 14). The layout is CAMS / MF Central
 * style: AMC section header -> "Folio No:" -> scheme lines (ISIN marker) ->
 * dated transaction rows (amount / units / price / balance, sometimes wrapped
 * onto the next physical line) -> "Closing Unit Balance" and "Valuation"
 * lines that give the current holding.
 *
 * Wrapped-row column assignment is heuristic (see flushPending) - every parsed
 * row is still reviewed on the reconciliation screen before it is accepted,
 * which is exactly the safety net MF.md 15 asks for.
 */
public class PdfCasParser implements CasReportParser {

    private static final Pattern DATE_LINE = Pattern.compile("^(\\d{1,2}-[A-Za-z]{3}-\\d{4})\\s+(.*)$");
    // statement header lines (MF Central detail CAS)
    private static final Pattern PAN_LINE = Pattern.compile("(?i)^PAN\\s*:\\s*([A-Za-z]{5}\\d{4}[A-Za-z])\\s*$");
    private static final Pattern PERIOD_LINE = Pattern.compile(
            "(?i)from\\s+date\\s*:\\s*([0-9A-Za-z/-]+)\\s+to\\s+date\\s*:\\s*([0-9A-Za-z/-]+)");
    // "Closing Unit Balance: 2,072.795 Nav as on ...: INR 88.9258 Valuation on ... : INR 1,84,324.95"
    private static final Pattern CLOSING_UNITS = Pattern.compile(
            "(?i)closing\\s+(?:unit\\s+)?balance\\s*:?\\s*(-?\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern VALUATION_VALUE = Pattern.compile(
            "(?i)valuation[^:]*:\\s*INR\\s*(-?\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern INVESTOR_NAME = Pattern.compile(
            "^[A-Z][A-Za-z.'-]+(?:\\s+[A-Z][A-Za-z.'-]+){1,3}$");
    private static final String[] NAME_DENY = {
            "Consolidated Account Statement", "Allocation by Asset Class",
            "SoA Holdings Demat Holdings", "No Folios Found"
    };
    private static final Pattern ADVISOR_SUFFIX = Pattern.compile("\\s*\\(Advisor:[^)]*\\)\\s*$");
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\(?-?\\d[\\d,]*(?:\\.\\d+)?\\)?$");
    /** CAS transaction rows have exactly four numeric columns (amount, units, price, balance). */
    private static final int MAX_COLUMNS = 4;
    private static final Pattern FOLIO_LINE = Pattern.compile("(?i)foli\\s*o\\s*(?:no\\.?|number|#)?\\s*[:\\-]?\\s*(.*)");
    // trailing number group - must be preceded by whitespace so inner groups
    // like "(5/10)" inside a description are never stripped
    private static final Pattern TRAILING_NUM = Pattern.compile("\\s+\\(?-?\\d[\\d,]*(?:\\.\\d+)?\\)?\\s*$");
    private static final Pattern ANY_NUM = Pattern.compile("-?\\(?[\\d][\\d,]*(?:\\.\\d+)?\\)?");

    private static final int KIND_CLOSING = 1;
    private static final int KIND_VALUATION = 2;

    // pending transaction row state (numbers may wrap onto the next line)
    private boolean pending;
    private LocalDate pDate;
    private String pDesc;
    private String pAmc;
    private String pFolio;
    private String pScheme;
    private List<String> pTokens = new ArrayList<>();
    private int pendingKind;
    private ParsedCasReport.Holding pendingHolding;

    @Override
    public ParsedCasReport parse(String fileName, byte[] data) throws Exception {
        ParsedCasReport out = new ParsedCasReport();
        String text;
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        }

        String amc = null;
        String folio = null;
        String scheme = null;
        String candidate = null;
        boolean schemeFound = false;
        Map<String, ParsedCasReport.Holding> holdings = new LinkedHashMap<>();

        pending = false;
        pTokens = new ArrayList<>();
        pendingKind = 0;
        pendingHolding = null;
        int lineNo = 0;

        for (String raw : text.split("\\R")) {
            String t = raw == null ? "" : raw.trim();
            if (t.isEmpty()) continue;
            lineNo++;

            // ---- AMC section header (e.g. "Axis Mutual Fund") ---------------
            if (isAmcHeader(t)) {
                String newAmc = t.replaceAll("\\s+", " ");
                if (!newAmc.equalsIgnoreCase(amc)) {
                    amc = newAmc;
                    folio = null;
                    scheme = null;
                    schemeFound = false;
                    candidate = null;
                }
                continue;
            }

            // ---- folio marker ----------------------------------------------
            Matcher fm = FOLIO_LINE.matcher(t);
            if (fm.find()) {
                if (pending) flushPending(out, holdings);
                folio = cleanFolio(fm.group(1));
                scheme = null;
                schemeFound = false;
                candidate = null;
                continue;
            }

            // ---- statement header: PAN / reporting period --------------------
            Matcher pm = PAN_LINE.matcher(t);
            if (pm.matches()) {
                if (out.pan == null || out.pan.isBlank()) out.pan = pm.group(1).toUpperCase(Locale.ROOT);
                continue;
            }
            Matcher pd = PERIOD_LINE.matcher(t);
            if (pd.find() && out.reportFrom == null) {
                out.reportFrom = parseDate(pd.group(1));
                out.reportTo = parseDate(pd.group(2));
                continue;
            }
            if (out.clientName == null && lineNo <= 40) {
                // the investor name sits as its own line in the page-1 address block
                String cand = t.replaceAll("\\s+", " ");
                if (INVESTOR_NAME.matcher(cand).matches() && !endsWithPeriod(cand)
                        && !isDeniedName(cand)) {
                    out.clientName = cand;
                }
            }

            // ---- report total (current value only on PDF) ---------------------
            if (t.contains("Market Value of Investments")) {
                Double v = trailingNumber(t);
                if (v != null && v > 0) {
                    out.rptCurrent = v;
                    out.totalsFound = true;
                }
                continue;
            }

            // ---- dated transaction row ----------------------------------------
            Matcher dm = DATE_LINE.matcher(t);
            if (dm.matches()) {
                LocalDate d = parseDate(dm.group(1));
                if (d != null) {
                    if (pending) flushPending(out, holdings);
                    String rest = dm.group(2);
                    // "01-APR-2024 to 31-MAR-2025" is a period line, not a txn
                    if (rest.toLowerCase(Locale.ROOT).startsWith("to ")
                            || rest.toLowerCase(Locale.ROOT).startsWith("through ")) {
                        continue;
                    }
                    pTokens = new ArrayList<>();
                    pDesc = stripTrailingNumbers(rest, pTokens);
                    pDate = d;
                    pAmc = amc;
                    pFolio = folio;
                    pScheme = effectiveScheme(scheme, candidate);
                    pending = true;
                }
                continue;
            }

            // continuation of the pending row: more wrapped numbers
            if (pending && isNumericOnly(t)) {
                extractTokens(t, pTokens);
                if (pTokens.size() >= 4) flushPending(out, holdings);
                continue;
            }
            if (pending) flushPending(out, holdings);

            // ---- closing balance / valuation lines ----------------------------
            boolean closing = contains(t, "closing unit balance") || contains(t, "closing balance")
                    || contains(t, "closing units");
            boolean valuation = contains(t, "valuation") && !contains(t, "date");
            if (closing || valuation) {
                ParsedCasReport.Holding h = currentHolding(holdings, amc, folio, effectiveScheme(scheme, candidate));
                // One physical line carries units + NAV + market value; taking the
                // trailing number for "closing" would put the valuation into units.
                Matcher cu = CLOSING_UNITS.matcher(t);
                if (h != null && cu.find()) {
                    h.units = CasReportParser.num(cu.group(1));
                    Matcher cv = VALUATION_VALUE.matcher(t);
                    if (cv.find()) {
                        h.current = CasReportParser.num(cv.group(1));
                        pendingKind = 0;
                    } else {
                        // valuation number sits on its own line right after this label
                        pendingKind = KIND_VALUATION;
                        pendingHolding = h;
                    }
                    continue;
                }
                Double v = trailingNumber(t);
                if (v != null && h != null) {
                    if (closing) h.units = v;
                    else h.current = v;
                    pendingKind = 0;
                } else {
                    // the number sits on its own line right after this label
                    pendingKind = closing ? KIND_CLOSING : KIND_VALUATION;
                    pendingHolding = h;
                }
                continue;
            }
            if (pendingKind != 0 && isNumericOnly(t)) {
                double v = CasReportParser.num(t);
                if (pendingKind == KIND_CLOSING && pendingHolding != null) pendingHolding.units = v;
                if (pendingKind == KIND_VALUATION && pendingHolding != null) pendingHolding.current = v;
                pendingKind = 0;
                continue;
            }
            pendingKind = 0;

            // ---- scheme detection (ISIN marker / header lines) ----------------
            if (!schemeFound && folio != null && !isFurniture(t)) {
                if (contains(t, "isin")) {
                    String pre = t.replaceAll("(?i)isin.*$", "").replaceAll("[-\\u2013:]+$", "").trim();
                    pre = cleanScheme(pre);
                    if (isSchemeLine(pre)) {
                        scheme = pre;
                        schemeFound = true;
                    } else if (isSchemeLine(cleanScheme(candidate))) {
                        scheme = cleanScheme(candidate);
                        schemeFound = true;
                    }
                } else if (isSchemeLine(t)) {
                    candidate = t;
                    if (scheme == null) scheme = t;
                }
            }
        }
        if (pending) flushPending(out, holdings);

        for (ParsedCasReport.Holding h : holdings.values()) {
            if (h.current <= 0 && h.units <= 0 && h.invested <= 0) continue;
            h.ret = h.current - h.invested;
            out.holdings.add(h);
        }
        out.rptHoldingsCount = out.holdings.size();
        out.rptTxnCount = out.transactions.size();
        return out;
    }

    // ------------------------------------------------------------- pending row

    /**
     * Assigns the collected number tokens to amount / units / nav. CAS columns
     * are (amount, units, price, balance); empty columns leave gaps and wrapped
     * rows only carry part of them, so decimal places disambiguate
     * (units/balance = 3-4 dp, amount/price = 2 dp).
     */
    private void flushPending(ParsedCasReport out, Map<String, ParsedCasReport.Holding> holdings) {
        if (!pending) return;
        pending = false;
        if (pDate == null) return;

        double amount = 0, units = 0, nav = 0;
        int n = pTokens.size();
        if (n >= 4) {
            amount = CasReportParser.num(pTokens.get(0));
            units = CasReportParser.num(pTokens.get(1));
            nav = CasReportParser.num(pTokens.get(2));
        } else if (n == 3) {
            if (dp(pTokens.get(0)) >= 3) {
                units = CasReportParser.num(pTokens.get(0));
                nav = dp(pTokens.get(1)) == 2 ? CasReportParser.num(pTokens.get(1)) : 0;
            } else {
                amount = CasReportParser.num(pTokens.get(0));
                units = CasReportParser.num(pTokens.get(1));
                nav = dp(pTokens.get(2)) == 2 ? CasReportParser.num(pTokens.get(2)) : 0;
            }
        } else if (n == 2) {
            if (dp(pTokens.get(0)) >= 3) {
                units = CasReportParser.num(pTokens.get(0));
                if (dp(pTokens.get(1)) == 2) nav = CasReportParser.num(pTokens.get(1));
            } else {
                amount = CasReportParser.num(pTokens.get(0));
                units = CasReportParser.num(pTokens.get(1));
            }
        } else if (n == 1) {
            if (dp(pTokens.get(0)) >= 3) units = CasReportParser.num(pTokens.get(0));
            else amount = CasReportParser.num(pTokens.get(0));
        }

        String desc = pDesc == null ? "" : pDesc.trim();
        String lower = desc.toLowerCase(Locale.ROOT);
        boolean outflow = lower.contains("redemption") || lower.contains("switch out")
                || lower.contains("withdrawal") || lower.contains("reversal");
        if (outflow) {
            if (amount > 0) amount = -amount;
            if (units > 0) units = -units;
        }

        ParsedCasReport.Txn txn = new ParsedCasReport.Txn();
        txn.schemeName = pScheme == null ? "" : pScheme;
        txn.description = desc;
        txn.date = pDate;
        txn.nav = nav;
        txn.units = units;
        txn.amount = amount;
        // the folio section this row was printed under - exact attribution, unlike
        // the xlsx / csv sheets which carry no folio column on transaction rows
        txn.folioNo = pFolio;
        out.transactions.add(txn);

        // invested value per folio = net of all reported cash flows
        ParsedCasReport.Holding h = currentHolding(holdings, pAmc, pFolio, pScheme);
        if (h != null) h.invested += amount;
    }

    private static ParsedCasReport.Holding currentHolding(Map<String, ParsedCasReport.Holding> holdings,
                                                          String amc, String folio, String scheme) {
        if (scheme == null || scheme.isBlank()) return null;
        String key = ((folio == null ? "" : folio) + "|" + scheme).toLowerCase(Locale.ROOT);
        ParsedCasReport.Holding h = holdings.get(key);
        if (h == null) {
            h = new ParsedCasReport.Holding();
            h.schemeName = scheme;
            h.amcName = amc == null ? "" : amc;
            h.folioNo = folio == null ? "" : folio;
            h.category = "";
            holdings.put(key, h);
        }
        return h;
    }

    // ----------------------------------------------------------------- parsing

    private static boolean isAmcHeader(String t) {
        if (t.length() > 70) return false;
        String l = t.toLowerCase(Locale.ROOT);
        if (!l.endsWith("mutual fund")) return false;
        return !contains(l, "folio") && !contains(l, "period") && !contains(l, "valuation")
                && !contains(l, "closing") && !contains(l, "isin") && !contains(l, "value")
                && !contains(l, "statement") && !contains(l, "page");
    }

    private static String cleanFolio(String cap) {
        String v = cap == null ? "" : cap.trim();
        v = v.replaceAll("^[\\s:.-]+", "").trim();
        // "14170335 KYC : OK" - the KYC status is not part of the folio number
        v = v.replaceAll("(?i)\\s+KYC\\b.*$", "").trim();
        int cut = v.indexOf("  ");
        if (cut > 0) v = v.substring(0, cut);
        return v.trim();
    }

    /** Drops the trailing "(Advisor: ...)" marker the PDF prints after scheme names. */
    private static String cleanScheme(String s) {
        if (s == null) return null;
        String v = ADVISOR_SUFFIX.matcher(s.trim()).replaceFirst("").trim();
        return v.replaceAll("\\s+", " ");
    }

    private static boolean endsWithPeriod(String s) {
        return s.endsWith(".");
    }

    private static boolean isDeniedName(String s) {
        for (String d : NAME_DENY) if (d.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static boolean isFurniture(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.matches(".*page \\d+ of \\d+.*")
                || l.contains("statement of account")
                || l.contains("consolidated account")
                || l.contains("continued")
                || l.contains("particulars")
                || l.contains("opening balance")
                || l.contains("period")
                || l.contains("report generated")
                || l.contains("transaction history")
                || l.equals("date");
    }

    private static boolean isSchemeLine(String t) {
        if (t == null || t.length() < 4) return false;
        String l = t.toLowerCase(Locale.ROOT);
        if (isFurniture(t)) return false;
        boolean keyword = l.contains("fund") || l.contains("plan") || l.contains("series")
                || l.contains("portfolio") || l.contains("trust");
        return keyword && !l.contains("systematic") && !l.contains("folio");
    }

    /** Removes the trailing number groups into tokens, returns the description. */
    private static String stripTrailingNumbers(String rest, List<String> tokens) {
        String s = rest;
        for (int i = 0; i < 6; i++) {
            // the four data columns end the row - never keep stripping, or the
            // instalment counter in "... Instalment No 6" is stolen from the description
            if (tokens.size() >= MAX_COLUMNS) break;
            Matcher m = TRAILING_NUM.matcher(s);
            if (!m.find()) break;
            String tok = m.group().trim();
            if (!tok.isEmpty()) tokens.add(0, tok);
            s = s.substring(0, m.start());
        }
        s = s.trim().replaceAll("\\s+", " ");
        // wrapped rows put the whole amount on the date line with the description
        // on the previous physical line - "7,999.60" is a column, not a description
        if (!s.isEmpty() && tokens.size() < MAX_COLUMNS && PURE_NUMBER.matcher(s).matches()) {
            tokens.add(0, s);
            s = "";
        }
        return s;
    }

    private static void extractTokens(String line, List<String> tokens) {
        Matcher m = ANY_NUM.matcher(line);
        while (m.find()) {
            tokens.add(m.group());
        }
    }

    private static boolean isNumericOnly(String t) {
        return ANY_NUM.matcher(t).find() && t.replaceAll("[\\s(),.\\-\\d]", "").isEmpty();
    }

    private static Double trailingNumber(String t) {
        List<String> tmp = new ArrayList<>();
        stripTrailingNumbers(t, tmp);
        if (tmp.isEmpty()) return null;
        return CasReportParser.num(tmp.get(tmp.size() - 1));
    }

    private static int dp(String num) {
        int dot = num.lastIndexOf('.');
        return dot < 0 ? 0 : num.length() - dot - 1;
    }

    private static String effectiveScheme(String scheme, String candidate) {
        if (scheme != null && !scheme.isBlank()) return scheme;
        return candidate;
    }

    private static boolean contains(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
