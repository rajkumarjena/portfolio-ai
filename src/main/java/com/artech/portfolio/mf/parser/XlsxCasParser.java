package com.artech.portfolio.mf.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the MF Central / CAMS CAS xlsx statement (MF.md 14, sample
 * cas_detailed_report_*.xlsx):
 *   - sheet 1 "Portfolio Details": client header, report totals, holding rows
 *   - sheet 2 "Transaction Details": client header, transaction rows
 * The layout is detected by content (header rows), not fixed row numbers, so
 * small formatting differences in other statements still parse.
 */
public class XlsxCasParser implements CasReportParser {

    @Override
    public ParsedCasReport parse(String fileName, byte[] data) throws Exception {
        ParsedCasReport out = new ParsedCasReport();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                processSheet(wb.getSheetAt(i), out);
            }
        }
        return out;
    }

    private void processSheet(Sheet sheet, ParsedCasReport out) {
        // Materialise non-empty rows as plain strings
        Map<Integer, String[]> rows = new HashMap<>();
        for (Row row : sheet) {
            String[] cells = new String[Math.max(row.getLastCellNum(), 0)];
            for (int c = 0; c < cells.length; c++) {
                cells[c] = value(row.getCell(c));
            }
            if (!isBlankRow(cells)) {
                rows.put(row.getRowNum(), cells);
            }
        }
        if (rows.isEmpty()) return;

        int headerRow = -1;
        String[] header = null;
        for (Map.Entry<Integer, String[]> e : rows.entrySet()) {
            for (String c : e.getValue()) {
                if (c != null && c.trim().equalsIgnoreCase("Scheme Name")) {
                    headerRow = e.getKey();
                    header = e.getValue();
                    break;
                }
            }
            if (headerRow >= 0) break;
        }
        if (headerRow < 0) return;

        readClientHeader(rows, headerRow, out);

        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null) cols.put(header[i].trim().toLowerCase(Locale.ROOT), i);
        }
        boolean holdingsSheet = cols.containsKey("invested value");
        boolean txnSheet = cols.containsKey("transaction description")
                || (cols.containsKey("nav") && cols.containsKey("amount"));

        if (holdingsSheet) {
            readHoldings(rows, headerRow, cols, out);
        } else if (txnSheet) {
            readTransactions(rows, headerRow, cols, out);
        }
    }

    private void readHoldings(Map<Integer, String[]> rows, int headerRow,
                              Map<String, Integer> cols, ParsedCasReport out) {
        int cScheme = cols.getOrDefault("scheme name", -1);
        int cAmc = cols.getOrDefault("amc name", -1);
        int cCat = cols.getOrDefault("category", -1);
        int cFolio = cols.getOrDefault("folio no.", -1);
        if (cFolio < 0) cFolio = cols.getOrDefault("folio no", -1);
        int cInv = cols.getOrDefault("invested value", -1);
        int cCur = cols.getOrDefault("current value", -1);
        int cRet = cols.getOrDefault("returns", -1);
        int cUnits = cols.getOrDefault("units", -1);

        for (Map.Entry<Integer, String[]> e : rows.entrySet()) {
            if (e.getKey() <= headerRow) continue;
            String[] r = e.getValue();
            String scheme = cell(r, cScheme);
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
        readTotals(rows, out);
        out.rptHoldingsCount = out.holdings.size();
    }

    private void readTransactions(Map<Integer, String[]> rows, int headerRow,
                                  Map<String, Integer> cols, ParsedCasReport out) {
        int cScheme = cols.getOrDefault("scheme name", -1);
        int cDesc = cols.getOrDefault("transaction description", -1);
        if (cDesc < 0) cDesc = cols.getOrDefault("description", -1);
        int cDate = cols.getOrDefault("date", -1);
        int cNav = cols.getOrDefault("nav", -1);
        int cUnits = cols.getOrDefault("units", -1);
        int cAmt = cols.getOrDefault("amount", -1);

        for (Map.Entry<Integer, String[]> e : rows.entrySet()) {
            if (e.getKey() <= headerRow) continue;
            String[] r = e.getValue();
            String scheme = cell(r, cScheme);
            String desc = cell(r, cDesc);
            if ((scheme == null || scheme.isBlank())
                    && (desc == null || desc.isBlank())) continue;
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

    /** Client identity on top of both sheets (rows 1-7 of the sample). */
    private void readClientHeader(Map<Integer, String[]> rows, int beforeRow, ParsedCasReport out) {
        for (Map.Entry<Integer, String[]> e : rows.entrySet()) {
            if (e.getKey() > beforeRow) break;
            String[] r = e.getValue();
            if (r.length < 2) continue;
            String label = r[0] == null ? "" : r[0].trim().toLowerCase(Locale.ROOT).replace(":", "");
            String val = r[1] == null ? "" : r[1].trim();
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

    /**
     * Report totals block: label row ("Total Investments" / "Current Portfolio
     * Value" / "Profit/Loss") with the values directly underneath (or inline).
     */
    private void readTotals(Map<Integer, String[]> rows, ParsedCasReport out) {
        for (Map.Entry<Integer, String[]> e : rows.entrySet()) {
            String[] r = e.getValue();
            if (r.length == 0) continue;
            String a = r[0] == null ? "" : r[0].trim().toLowerCase(Locale.ROOT);
            if (!a.contains("total investments")) continue;

            String[] below = rows.get(e.getKey() + 1);
            // labels can sit in consecutive columns with values underneath
            for (Map.Entry<Integer, String[]> probe : rows.entrySet()) {
                if (probe.getKey() <= e.getKey()) continue;
                String[] cand = probe.getValue();
                boolean numericLine = cand.length > 0 && CasReportParser.looksNumeric(cand[0]);
                if (numericLine) {
                    below = cand;
                    break;
                }
            }
            double invested = 0, current = 0, profit = 0;
            for (Map.Entry<Integer, String[]> lab : rows.entrySet()) {
                if (lab.getKey() != e.getKey()) continue;
                String[] lr = lab.getValue();
                for (int c = 0; c < lr.length; c++) {
                    String label = lr[c] == null ? "" : lr[c].trim().toLowerCase(Locale.ROOT);
                    if (label.isEmpty()) continue;
                    double v = Double.NaN;
                    if (below != null && c < below.length && CasReportParser.looksNumeric(below[c])) {
                        v = CasReportParser.num(below[c]);
                    } else if (c + 1 < lr.length && CasReportParser.looksNumeric(lr[c + 1])) {
                        v = CasReportParser.num(lr[c + 1]);
                    }
                    if (Double.isNaN(v)) continue;
                    if (label.contains("total investment")) invested = v;
                    else if (label.contains("current portfolio")) current = v;
                    else if (label.contains("profit")) profit = v;
                    else if (label.contains("unit")) { out.rptUnits = v; out.unitsTotalFound = true; }
                }
                break;
            }
            if (invested != 0 || current != 0) {
                out.rptInvested = invested;
                out.rptCurrent = current;
                out.rptProfit = profit;
                out.totalsFound = true;
            }
            return;
        }
    }

    // ----------------------------------------------------------------- helpers

    private static String cell(String[] row, int idx) {
        return row == null || idx < 0 || idx >= row.length ? null : row[idx];
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlankRow(String[] cells) {
        for (String c : cells) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    /** Renders a cell as the raw string the shared parsers expect. */
    private static String value(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate d = cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                    return d.toString(); // yyyy-MM-dd - parses back fine
                }
                return BigDecimal.valueOf(cell.getNumericCellValue())
                        .stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
