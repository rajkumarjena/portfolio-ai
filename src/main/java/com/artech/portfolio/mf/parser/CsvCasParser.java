package com.artech.portfolio.mf.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSV variant of the MF Central / CAMS CAS export (MF.md 14 - xlsx and PDF are
 * required, csv is the extra provision). Same column layout as the xlsx:
 * client header first, then a "Scheme Name" header row, then data rows. The
 * parser is fully header-driven so extra or reordered columns still work.
 */
public class CsvCasParser implements CasReportParser {

    @Override
    public ParsedCasReport parse(String fileName, byte[] data) throws Exception {
        ParsedCasReport out = new ParsedCasReport();
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        List<List<String>> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) continue;
            rows.add(splitCsv(line));
        }

        int headerRow = -1;
        for (int i = 0; i < rows.size(); i++) {
            for (String c : rows.get(i)) {
                if (c != null && c.trim().equalsIgnoreCase("Scheme Name")) {
                    headerRow = i;
                    break;
                }
            }
            if (headerRow >= 0) break;
        }
        if (headerRow < 0) {
            throw new IllegalArgumentException("No 'Scheme Name' header row found in CSV");
        }

        readClientHeader(rows, headerRow, out);

        List<String> header = rows.get(headerRow);
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            cols.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        boolean holdingsSheet = cols.containsKey("invested value");
        if (holdingsSheet) {
            readHoldings(rows, headerRow, cols, out);
        } else {
            readTransactions(rows, headerRow, cols, out);
        }
        return out;
    }

    private void readHoldings(List<List<String>> rows, int headerRow,
                              Map<String, Integer> cols, ParsedCasReport out) {
        int cScheme = cols.getOrDefault("scheme name", -1);
        int cAmc = cols.getOrDefault("amc name", -1);
        int cCat = cols.getOrDefault("category", -1);
        int cFolio = cols.getOrDefault("folio no.", cols.getOrDefault("folio no", -1));
        int cInv = cols.getOrDefault("invested value", -1);
        int cCur = cols.getOrDefault("current value", -1);
        int cRet = cols.getOrDefault("returns", -1);
        int cUnits = cols.getOrDefault("units", -1);

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            String scheme = cell(r, cScheme);
            // totals rows must be recognised before the "total" scheme skip -
            // their first cell IS "Total Investments", otherwise the header-level
            // validation (MF.md 15) can never read them
            if (looksLikeTotalsRow(r)) {
                readTotalsRow(r, out);
                continue;
            }
            if (scheme == null || scheme.isBlank() || scheme.toLowerCase(Locale.ROOT).contains("total")) continue;
            ParsedCasReport.Holding h = new ParsedCasReport.Holding();
            h.schemeName = scheme.trim();
            h.amcName = nvl(cell(r, cAmc));
            h.category = nvl(cell(r, cCat));
            h.folioNo = nvl(cell(r, cFolio));
            h.invested = CasReportParser.num(cell(r, cInv));
            h.current = CasReportParser.num(cell(r, cCur));
            h.ret = CasReportParser.num(cell(r, cRet));
            h.units = CasReportParser.num(cell(r, cUnits));
            out.holdings.add(h);
        }
        out.rptHoldingsCount = out.holdings.size();
    }

    private void readTransactions(List<List<String>> rows, int headerRow,
                                  Map<String, Integer> cols, ParsedCasReport out) {
        int cScheme = cols.getOrDefault("scheme name", -1);
        int cDesc = cols.getOrDefault("transaction description", cols.getOrDefault("description", -1));
        int cDate = cols.getOrDefault("date", -1);
        int cNav = cols.getOrDefault("nav", -1);
        int cUnits = cols.getOrDefault("units", -1);
        int cAmt = cols.getOrDefault("amount", -1);

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            String scheme = cell(r, cScheme);
            String desc = cell(r, cDesc);
            if ((scheme == null || scheme.isBlank()) && (desc == null || desc.isBlank())) continue;
            // trailing summary rows carry no date and are not transactions
            if (scheme != null && scheme.toLowerCase(Locale.ROOT).contains("total")) continue;

            ParsedCasReport.Txn t = new ParsedCasReport.Txn();
            t.schemeName = scheme == null ? "" : scheme.trim();
            t.description = desc == null ? "" : desc.trim();
            t.date = parseDate(cell(r, cDate));
            t.nav = CasReportParser.num(cell(r, cNav));
            t.units = CasReportParser.num(cell(r, cUnits));
            t.amount = CasReportParser.num(cell(r, cAmt));
            out.transactions.add(t);
        }
        out.rptTxnCount = out.transactions.size();
    }

    private void readClientHeader(List<List<String>> rows, int beforeRow, ParsedCasReport out) {
        for (int i = 0; i < beforeRow && i < 30; i++) {
            List<String> r = rows.get(i);
            String label, val;
            if (r.size() >= 2) {
                label = r.get(0);
                val = r.get(1);
            } else {
                String one = r.get(0);
                int colon = one.indexOf(':');
                if (colon <= 0) continue;
                label = one.substring(0, colon);
                val = one.substring(colon + 1);
            }
            label = label.trim().toLowerCase(Locale.ROOT).replace(":", "");
            val = val == null ? "" : val.trim();
            if (val.isEmpty()) continue;
            switch (label) {
                case "name", "client name", "investor name" -> out.clientName = val;
                case "pan", "pan no", "pan number" -> out.pan = val;
                case "from date" -> out.reportFrom = parseDate(val);
                case "to date" -> out.reportTo = parseDate(val);
                default -> { }
            }
        }
    }

    private boolean looksLikeTotalsRow(List<String> r) {
        int labels = 0;
        for (String c : r) {
            String s = c == null ? "" : c.toLowerCase(Locale.ROOT);
            if (s.contains("total investment") || s.contains("current portfolio")
                    || s.contains("profit")) labels++;
        }
        return labels >= 2;
    }

    private void readTotalsRow(List<String> r, ParsedCasReport out) {
        for (String c : r) {
            String s = c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
            if (s.contains("total investment")) {
                out.rptInvested = CasReportParser.num(after(r, c));
                out.totalsFound = true;
            } else if (s.contains("current portfolio")) {
                out.rptCurrent = CasReportParser.num(after(r, c));
                out.totalsFound = true;
            } else if (s.contains("profit")) {
                out.rptProfit = CasReportParser.num(after(r, c));
            }
        }
    }

    /** Value may be inline after the label or on the next cell. */
    private String after(List<String> r, String labelCell) {
        int idx = r.indexOf(labelCell);
        if (idx >= 0 && idx + 1 < r.size() && CasReportParser.looksNumeric(r.get(idx + 1))) {
            return r.get(idx + 1);
        }
        return "0";
    }

    // ----------------------------------------------------------------- helpers

    static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cells.add(cur.toString());
        return cells;
    }

    private static String cell(List<String> row, int idx) {
        return row == null || idx < 0 || idx >= row.size() ? null : row.get(idx);
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
