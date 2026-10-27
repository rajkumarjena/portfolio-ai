package com.artech.portfolio.mf.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a KFINTECH Consolidated Account Statement (detailed CAS) PDF and
 * produces the {@link KfCasReport} that mirrors the report's JSON form
 * (MF.md 4/5).
 *
 * <h2>How the text is read</h2>
 * The page is a table: a PORTFOLIO SUMMARY block on page 1, then per-AMC /
 * per-folio / per-scheme blocks with the ledger (Date | Transaction | Amount |
 * Units | Price | Unit Balance) between them. Instead of trusting the text
 * order the stripper would produce, every glyph is captured with its
 * coordinates and re-assembled here:
 *
 * <ol>
 *   <li>glyphs are clustered into visual rows (y gap),</li>
 *   <li>each row into cells (x gap),</li>
 *   <li>each cell into y-layers when the same cell carries text twice.</li>
 * </ol>
 *
 * <h2>The overprinted date column</h2>
 * This report draws every ledger date twice: once on its own row and once
 * 10.08pt lower, which lands on the next row (or on the closing line). Where
 * the two dates differ they overlap and interleave into an unreadable string
 * such as "265-SOecpt-2022". Because duplicate glyphs are suppressed by
 * default by the stripper, the true date can even come out truncated - which
 * is why duplicates are kept ({@code setSuppressDuplicateOverlappingText(false)})
 * and each date cell is split into its two y-layers: the lower layer sits on
 * the row band and carries the row's own date, the upper one is the previous
 * row's date printed again. Every row is still reviewed on the reconciliation
 * screen before anything is accepted (MF.md 13/16).
 * </p>
 *
 * No statement-specific value is hard coded (MF.md 15): column positions come
 * from the printed table header, dates/numbers from the cells themselves.
 */
public final class KfCasParser {

    public static final String PASSWORD_REQUIRED_MSG =
            "Mutual Fund report is password protected, please fill the password then "
                    + "submit the 'Upload and Reconcile' button";
    public static final String PASSWORD_WRONG_MSG =
            "Mutual Fund report password is incorrect, please check the password and "
                    + "submit the 'Upload and Reconcile' button";
    public static final String UNREADABLE_MSG =
            "Unable to read the Mutual Fund report, please upload a KFINTECH detailed CAS pdf";

    // ---- layout tolerances (points, from the report's own geometry) --------
    /** Two text lines further apart than this start a new visual row. */
    private static final double ROW_Y_GAP = 2.0;
    /** Horizontal gap that separates two cells of a row. */
    private static final double CELL_X_GAP = 6.0;
    /** Vertical gap that separates two text layers inside one cell. */
    private static final double LAYER_Y_GAP = 0.4;
    /** A whitespace glyph intruding this far into the previous glyph is a
     *  rendering artefact (e.g. "S ystematic") and is dropped. */
    private static final double SPACE_OVERLAP = 0.5;

    private static final Pattern DATE_TEXT =
            Pattern.compile("^(\\d{1,2})-([A-Za-z]{3})-(\\d{4})$");
    private static final Pattern PERIOD_LINE = Pattern.compile("^(.*?)\\s+To\\s+(.*)$");
    private static final Pattern FOLIO_LINE = Pattern.compile("(?i)^folio\\s*no\\.?\\s*[:\\-]?\\s*(.*)$");
    private static final Pattern NOMINEE_LINE = Pattern.compile("(?i)^nominee\\s*[123]\\s*:\\s*(.*)$");
    private static final Pattern EMAIL_LINE = Pattern.compile("(?i)^email\\s*id\\s*:\\s*(.*)$");
    private static final Pattern MOBILE_LINE = Pattern.compile("(?i)^mobile\\s*:\\s*(.*)$");
    private static final Pattern PAN_LINE = Pattern.compile("(?i)^pan\\s*:\\s*(.*)$");
    private static final Pattern KYC_LINE = Pattern.compile("(?i)^kyc\\s*:\\s*(.*)$");
    private static final Pattern PAN_VALUE = Pattern.compile("^[A-Za-z]{5}\\d{4}[A-Za-z]$");
    private static final Pattern CLOSING_UNITS = Pattern.compile(
            "(?i)closing\\s+(?:unit\\s+)?balance\\s*:\\s*([-0-9,.]+)");
    private static final Pattern NAV_LINE = Pattern.compile(
            "(?i)nav\\s+on\\s+([0-9A-Za-z\\-]+)\\s*:\\s*INR\\s*([-0-9,.]+)");
    private static final Pattern COST_LINE = Pattern.compile(
            "(?i)total\\s+cost\\s+value\\s*:\\s*INR\\s*([-0-9,.]+)");
    private static final Pattern MARKET_LINE = Pattern.compile(
            "(?i)market\\s+value\\s+on\\s+([0-9A-Za-z\\-]+)\\s*:\\s*INR\\s*([-0-9,.]+)");
    private static final Pattern ISIN_VALUE = Pattern.compile("(?i)ISIN\\s*:\\s*([A-Za-z0-9]{8,20})");
    private static final Pattern ISIN_PART = Pattern.compile("(?i)\\s*-?\\s*ISIN\\s*:\\s*[A-Za-z0-9]{8,20}.*$");
    private static final Pattern ADVISOR_PART = Pattern.compile("(?i)\\(\\s*advisor\\s*:\\s*([^)]*)\\)");
    private static final Pattern FORMERLY_PART = Pattern.compile(
            "(?i)\\((?:formerly|erstwhile)[^)]*\\)");
    private static final Pattern DEMAT_PART = Pattern.compile("(?i)\\((?:Demat|Non-Demat).*");
    private static final Pattern NUMBER_TEXT = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern NOMINEE_CELL = Pattern.compile("(?i)^nominee\\s*[123]\\s*:");

    /** Printed ledger header labels; their x positions give the column starts. */
    private static final String[] COLUMN_LABELS =
            {"date", "transaction", "amount", "units", "price", "balance"};

    // ---------------------------------------------------------------- errors

    /** The pdf is encrypted and no password was supplied (MF.md 3). */
    public static final class PasswordRequiredException extends RuntimeException {
        public PasswordRequiredException(String message) {
            super(message);
        }
    }

    /** The supplied password does not open the pdf. */
    public static final class PasswordMismatchException extends RuntimeException {
        public PasswordMismatchException(String message) {
            super(message);
        }
    }

    /** The pdf opened but no statement content could be read from it. */
    public static final class StatementParseException extends RuntimeException {
        public StatementParseException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ parse

    /**
     * @param data     uploaded pdf bytes
     * @param password password of the protected pdf, may be null when the file
     *                 is not encrypted
     */
    public KfCasReport parse(byte[] data, String password) throws Exception {
        PDDocument doc;
        try {
            doc = Loader.loadPDF(data, password == null ? "" : password);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password == null || password.isBlank()) {
                throw new PasswordRequiredException(PASSWORD_REQUIRED_MSG);
            }
            throw new PasswordMismatchException(PASSWORD_WRONG_MSG);
        }
        try (PDDocument d = doc) {
            Extractor extractor = new Extractor();
            extractor.setSortByPosition(true);
            // keep overprinted glyphs: the date column is drawn twice (see class doc)
            extractor.setSuppressDuplicateOverlappingText(false);
            extractor.writeText(d, new StringWriter());

            KfCasReport report = build(extractor.pages);
            if (report.folios.isEmpty() || report.folios.stream().noneMatch(f -> !f.schemes.isEmpty())) {
                throw new StatementParseException(UNREADABLE_MSG);
            }
            return report;
        }
    }

    // ------------------------------------------------------------- extraction

    /** One positioned character of the document. */
    private static final class Glyph {
        final double x;
        final double y;
        final double w;
        final String ch;

        Glyph(double x, double y, double w, String ch) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.ch = ch;
        }

        double end() {
            return x + w;
        }

        boolean space() {
            return ch.isBlank();
        }
    }

    /** All glyphs of one text line inside one cell, grouped by y-layer. */
    private static final class Cell {
        final double x;
        final List<Layer> layers; // y ascending

        Cell(double x, List<Layer> layers) {
            this.x = x;
            this.layers = layers;
        }

        Layer bottom() {
            return layers.get(layers.size() - 1);
        }

        /** All layers read top to bottom - the cell text as a human reads it. */
        String text() {
            StringBuilder sb = new StringBuilder();
            for (Layer l : layers) {
                if (sb.length() > 0 && !l.text.startsWith(" ") && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(l.text);
            }
            return collapse(sb.toString());
        }
    }

    private static final class Layer {
        final double y;
        final String text;

        Layer(double y, String text) {
            this.y = y;
            this.text = text;
        }
    }

    private static final class Row {
        double y;
        List<Cell> cells = new ArrayList<>();
    }

    /** Collects positioned glyphs page by page. */
    private static final class Extractor extends PDFTextStripper {
        final Map<Integer, List<Glyph>> pages = new TreeMap<>();
        private int pageNo;

        Extractor() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            pageNo = getCurrentPageNo();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            List<Glyph> glyphs = pages.computeIfAbsent(pageNo, p -> new ArrayList<>());
            for (TextPosition tp : positions) {
                // The page footer ("Page 12 of 54 KFINCASWS-... Version 1.0") is
                // printed rotated along the page edge. Its glyphs arrive one per
                // visual row and would otherwise be read as a description
                // continuation of the last ledger row above it. Table text always
                // runs horizontally, so anything drawn at another angle is page
                // furniture and is dropped here (MF.md 15 - nothing about this
                // statement is hard coded).
                if (Math.abs(tp.getDir() % 360f) > 0.01f) {
                    continue;
                }
                glyphs.add(new Glyph(tp.getXDirAdj(), tp.getY(),
                        tp.getWidthDirAdj(), tp.getUnicode()));
            }
        }
    }

    private static List<Row> toRows(List<Glyph> glyphs) {
        List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x));
        List<Row> rows = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            double prev = sorted.get(i).y;
            List<Glyph> band = new ArrayList<>();
            int j = i;
            while (j < sorted.size() && sorted.get(j).y - prev <= ROW_Y_GAP) {
                band.add(sorted.get(j));
                prev = sorted.get(j).y;
                j++;
            }
            Row row = new Row();
            row.y = band.get(0).y;
            row.cells = toCells(band);
            rows.add(row);
            i = j;
        }
        return rows;
    }

    private static List<Cell> toCells(List<Glyph> row) {
        List<Glyph> sorted = new ArrayList<>(row);
        sorted.sort(Comparator.comparingDouble(g -> g.x));
        List<Cell> cells = new ArrayList<>();
        List<Glyph> run = new ArrayList<>();
        double lastEnd = -1;
        for (Glyph g : sorted) {
            if (!run.isEmpty() && g.x - lastEnd > CELL_X_GAP) {
                cells.add(toCell(run));
                run = new ArrayList<>();
            }
            run.add(g);
            lastEnd = Math.max(lastEnd, g.end());
        }
        if (!run.isEmpty()) {
            cells.add(toCell(run));
        }
        return cells;
    }

    private static Cell toCell(List<Glyph> run) {
        List<Glyph> byY = new ArrayList<>(run);
        byY.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x));
        List<Layer> layers = new ArrayList<>();
        List<Glyph> layer = new ArrayList<>();
        double base = 0;
        for (Glyph g : byY) {
            if (!layer.isEmpty() && g.y - base > LAYER_Y_GAP) {
                layers.add(toLayer(layer));
                layer = new ArrayList<>();
            }
            if (layer.isEmpty()) {
                base = g.y;
            }
            layer.add(g);
        }
        if (!layer.isEmpty()) {
            layers.add(toLayer(layer));
        }
        return new Cell(run.get(0).x, layers);
    }

    /** Joins one layer's glyphs left to right, dropping overlapping blanks. */
    private static Layer toLayer(List<Glyph> layer) {
        List<Glyph> byX = new ArrayList<>(layer);
        byX.sort(Comparator.comparingDouble(g -> g.x));
        StringBuilder sb = new StringBuilder();
        double lastEnd = -1;
        for (Glyph g : byX) {
            if (g.space() && lastEnd > 0 && g.x < lastEnd - SPACE_OVERLAP) {
                continue; // blank drawn on top of the previous letter
            }
            sb.append(g.ch);
            if (!g.space()) {
                lastEnd = g.end();
            }
        }
        return new Layer(byX.get(0).y, sb.toString());
    }

    // --------------------------------------------------------- report builder

    private KfCasReport build(Map<Integer, List<Glyph>> pages) {
        KfCasReport report = new KfCasReport();
        Builder builder = new Builder(report);
        for (Map.Entry<Integer, List<Glyph>> entry : pages.entrySet()) {
            builder.page = entry.getKey();
            for (Row row : toRows(entry.getValue())) {
                builder.accept(row);
            }
        }
        builder.finish();
        return report;
    }

    /**
     * Walks the printed rows of the statement and fills the report. The rules
     * are purely structural (what the row looks like), never value based.
     */
    private static final class Builder {
        final KfCasReport report;
        final Map<String, KfCasReport.Folio> foliosByNo = new LinkedHashMap<>();

        int page;
        boolean investorDone;
        boolean summaryMode;
        double addrX = -1;

        // table column starts, derived from the printed header
        boolean columnsReady;
        double cTxn;
        double cAmount;
        double cUnits;
        double cPrice;
        double cBalance;
        final Map<String, Double> headerLabels = new LinkedHashMap<>();
        // PORTFOLIO SUMMARY column starts
        boolean summaryColumnsReady;
        double sLabelMax;
        double sCostMax;

        String amc;
        KfCasReport.Folio folio;
        KfCasReport.Scheme scheme;
        BigDecimal pendingOpen;
        /** last ledger row written, so a wrapped description can continue on it */
        KfCasReport.Txn lastTxn;
        double lastTxnY;

        Builder(KfCasReport report) {
            this.report = report;
        }

        void accept(Row row) {
            if (row.cells.isEmpty()) {
                return;
            }
            String joined = join(row.cells, Cell::text);

            // 1 - statement period (printed in every page header)
            if (report.statementPeriod.from == null) {
                Matcher period = PERIOD_LINE.matcher(joined);
                if (period.find() && !period.group(1).isBlank() && isDate(period.group(1))) {
                    report.statementPeriod.from = period.group(1).trim();
                    report.statementPeriod.to = period.group(2).trim();
                    return;
                }
            }

            // 2 - investor block of page 1 (email / name / address / mobile)
            if (page == 1 && !summaryMode && !investorDone && takeInvestor(row)) {
                return;
            }

            // 3 - PORTFOLIO SUMMARY banner
            if (containsCell(row, "portfolio summary")) {
                summaryMode = true;
                investorDone = true; // the investor block sits above the banner
                return;
            }

            // 4 - the ledger header spans a few physical lines (Date / Amount /
            //     Units are printed at slightly different heights), so its labels
            //     are collected until the six column starts are known
            if (!columnsReady && takeHeaderLabels(row)) {
                return;
            }

            // 5 - summary table header -> its column starts
            if (summaryMode && !summaryColumnsReady && containsCell(row, "mutual fund")
                    && containsCell(row, "cost value")) {
                Double label = cellX(row, "mutual fund");
                Double cost = cellX(row, "cost value");
                Double market = cellX(row, "market value");
                if (label != null && cost != null && market != null) {
                    sLabelMax = (label + cost) / 2;
                    sCostMax = (cost + market) / 2;
                    summaryColumnsReady = true;
                }
                return;
            }

            // 6 - PORTFOLIO SUMMARY rows
            if (summaryMode && summaryColumnsReady && takeSummaryRow(row)) {
                return;
            }

            // 7 - AMC section header ("Axis Mutual Fund", "Quant MF", ...)
            if (isAmcHeader(row)) {
                amc = row.cells.get(0).text();
                folio = null;
                scheme = null;
                pendingOpen = null;
                lastTxn = null;
                return;
            }

            // 8 - folio header with the investor's PAN / KYC status
            String first = row.cells.get(0).text();
            if (FOLIO_LINE.matcher(first).find()) {
                takeFolio(row, first);
                return;
            }

            // 9 - scheme header: "<rta code>-<scheme> - ISIN:<isin>" + registrar
            if (joined.contains("Registrar :") || joined.contains("Registrar:")
                    || ISIN_VALUE.matcher(joined).find()) {
                if (takeScheme(row, joined)) {
                    return;
                }
            }

            // 10 - nominee line(s): "Nominee 1 : <name>" ... (only ever a header
            //      line - a ledger description mentioning a nominee is a transaction)
            if (hasNomineeCell(row)) {
                takeNominees(row);
                return;
            }

            // 11 - "Opening Unit Balance <units>"
            if (joined.contains("Opening Unit Balance")) {
                takeOpening(row);
                return;
            }

            // 12 - closing line: units, NAV, cost and market value
            if (joined.contains("Closing Unit Balance")) {
                takeClosing(joined);
                return;
            }

            // 13 - a ledger row of the current scheme
            if (columnsReady && scheme != null) {
                LocalDate date = ledgerDate(row);
                if (date != null) {
                    takeLedgerRow(row, date);
                    return;
                }
                // 13b - a long description (or its trailing reference number)
                //       continues on the physical line(s) below its own row
                if (lastTxn != null && takeDescriptionTail(row)) {
                    return;
                }
            }

            // 14 - the folio's investor name, printed under the folio header
            if (folio != null && scheme == null && row.cells.size() == 1) {
                String t = row.cells.get(0).text();
                if (!t.isEmpty() && t.length() <= 60 && !t.contains(":")
                        && !Character.isDigit(t.charAt(0)) && ledgerDate(row) == null) {
                    if (folio.name == null) {
                        folio.name = t;
                    }
                }
            }
        }

        void finish() {
            for (KfCasReport.Folio f : report.folios) {
                for (KfCasReport.Scheme s : f.schemes) {
                    s.closeCalculated = calcClose(s);
                }
            }
        }

        /** Collects the printed column labels of the ledger header. */
        private boolean takeHeaderLabels(Row row) {
            boolean any = false;
            for (Cell c : row.cells) {
                String t = c.text().toLowerCase(Locale.ROOT).trim();
                for (String label : COLUMN_LABELS) {
                    if (t.equals(label) || t.startsWith(label + " ") || t.startsWith(label + " (")
                            || (label.equals("balance") && t.endsWith(" balance"))) {
                        headerLabels.putIfAbsent(label, c.x);
                        any = true;
                    }
                }
            }
            if (!any) {
                return false;
            }
            if (headerLabels.size() == COLUMN_LABELS.length) {
                double date = headerLabels.get("date");
                double txn = headerLabels.get("transaction");
                double amount = headerLabels.get("amount");
                double units = headerLabels.get("units");
                double price = headerLabels.get("price");
                double balance = headerLabels.get("balance");
                cTxn = (date + txn) / 2;
                cAmount = (txn + amount) / 2;
                cUnits = (amount + units) / 2;
                cPrice = (units + price) / 2;
                cBalance = (price + balance) / 2;
                columnsReady = true;
                summaryMode = false;
            }
            return true;
        }

        // -------------------------------------------------------- investor

        /** email -> name -> address lines -> mobile, all in the left column. */
        private boolean takeInvestor(Row row) {
            Cell left = null;
            for (Cell c : row.cells) {
                if (addrX < 0 ? c.x < 100 : c.x <= addrX + 30) {
                    left = c;
                }
            }
            if (left == null) {
                return false;
            }
            String t = left.text();
            Matcher mail = EMAIL_LINE.matcher(t);
            if (mail.find()) {
                report.investorInfo.email = mail.group(1).trim();
                addrX = left.x;
                return true;
            }
            if (addrX < 0) {
                return false; // no header block started yet
            }
            Matcher mobile = MOBILE_LINE.matcher(t);
            if (mobile.find()) {
                report.investorInfo.mobile = mobile.group(1).trim();
                investorDone = true;
                return true;
            }
            if (report.investorInfo.name == null && !t.isBlank()) {
                report.investorInfo.name = t;
                return true;
            }
            String address = report.investorInfo.address;
            report.investorInfo.address = address == null || address.isBlank()
                    ? t : address + "\n" + t;
            return true;
        }

        // ---------------------------------------------------------- summary

        private boolean takeSummaryRow(Row row) {
            List<String> texts = new ArrayList<>();
            String label = null;
            String cost = null;
            String market = null;
            for (Cell c : row.cells) {
                String t = c.text();
                texts.add(t);
                if (c.x >= sCostMax && num(t) != null) {
                    market = num(t);
                } else if (c.x >= sLabelMax && num(t) != null && cost == null) {
                    cost = num(t);
                } else if (c.x < sLabelMax && label == null && num(t) == null) {
                    label = t;
                }
            }
            if (label == null || (cost == null && market == null)) {
                return false;
            }
            KfCasReport.SummaryRow s = new KfCasReport.SummaryRow();
            s.amc = label;
            s.cost = cost;
            s.market = market;
            s.total = label.equalsIgnoreCase("Total");
            report.summary.add(s);
            return true;
        }

        // ------------------------------------------------------- folio block

        private void takeFolio(Row row, String first) {
            Matcher m = FOLIO_LINE.matcher(first);
            String no = m.find() ? m.group(1).trim() : first;
            folio = foliosByNo.get(no);
            if (folio == null) {
                folio = new KfCasReport.Folio();
                folio.folio = no;
                folio.amc = amc;
                foliosByNo.put(no, folio);
                report.folios.add(folio);
            }
            scheme = null;
            pendingOpen = null;
            lastTxn = null;
            for (Cell c : row.cells) {
                String t = c.text();
                Matcher pan = PAN_LINE.matcher(t);
                Matcher kyc = KYC_LINE.matcher(t);
                if (pan.find()) {
                    String v = pan.group(1).trim();
                    if (PAN_VALUE.matcher(v).matches()) {
                        folio.pan = v;
                    } else {
                        folio.panKyc = v;
                    }
                } else if (kyc.find()) {
                    folio.kyc = kyc.group(1).trim();
                }
            }
        }

        // ------------------------------------------------------- scheme block

        private boolean takeScheme(Row row, String joined) {
            if (folio == null) {
                report.parseWarnings.add("Scheme block without a folio header skipped: " + joined);
                return false;
            }
            // the scheme text sits in the cell that carries the ISIN, or in the
            // cell left of the registrar marker
            String base = null;
            int baseIdx = -1;
            for (int i = 0; i < row.cells.size(); i++) {
                String t = row.cells.get(i).text();
                if (ISIN_VALUE.matcher(t).find()) {
                    base = t;
                    baseIdx = i;
                    break;
                }
            }
            if (base == null) {
                for (int i = 0; i < row.cells.size(); i++) {
                    if (row.cells.get(i).text().contains("Registrar")) {
                        if (i > 0) {
                            base = row.cells.get(i - 1).text();
                            baseIdx = i - 1;
                        }
                        break;
                    }
                }
            }
            if (base == null || base.isBlank() || baseIdx < 0) {
                return false;
            }
            // ... and is only a scheme line when it carries the rta code prefix
            String cleaned = base.trim();
            String rtaCode = null;
            int dash = cleaned.indexOf('-');
            if (dash > 0 && dash <= 12 && cleaned.substring(0, dash).matches("[A-Za-z0-9]+")) {
                rtaCode = cleaned.substring(0, dash);
                cleaned = cleaned.substring(dash + 1);
            }
            Matcher isinM = ISIN_VALUE.matcher(cleaned);
            String isin = isinM.find() ? isinM.group(1) : null;
            cleaned = ISIN_PART.matcher(cleaned).replaceFirst("");
            Matcher adv = ADVISOR_PART.matcher(cleaned);
            String advisor = null;
            if (adv.find()) {
                advisor = adv.group(1).trim();
                if (advisor.isEmpty()) {
                    advisor = null;
                }
                cleaned = adv.replaceFirst("");
            }
            cleaned = FORMERLY_PART.matcher(cleaned).replaceAll("");
            cleaned = DEMAT_PART.matcher(cleaned).replaceAll("");
            cleaned = collapse(cleaned).trim().replaceAll("[\\s-]+$", "").trim();

            KfCasReport.Scheme s = new KfCasReport.Scheme();
            s.scheme = cleaned;
            s.rtaCode = rtaCode;
            s.advisor = advisor;
            s.isin = isin;
            Matcher rtaM = Pattern.compile("(?i)Registrar\\s*:\\s*(\\S+)").matcher(joined);
            s.rta = rtaM.find() ? rtaM.group(1).trim() : null;
            if (s.scheme == null || s.scheme.isBlank()) {
                return false;
            }
            scheme = s;
            folio.schemes.add(s);
            pendingOpen = null;
            lastTxn = null;
            return true;
        }

        private void takeNominees(Row row) {
            boolean found = false;
            for (Cell c : row.cells) {
                String t = c.text();
                Matcher n = NOMINEE_LINE.matcher(t);
                if (n.find()) {
                    found = true;
                    String name = n.group(1).trim();
                    if (!name.isEmpty() && scheme != null) {
                        scheme.nominees.add(name);
                    }
                } else if (num(t) != null) {
                    // the opening balance occasionally sits on this line
                    pendingOpen = new BigDecimal(num(t));
                }
            }
            if (!found) {
                report.parseWarnings.add("Nominee line without nominee cells skipped");
            }
        }

        private void takeOpening(Row row) {
            BigDecimal open = null;
            for (int i = row.cells.size() - 1; i >= 0; i--) {
                String t = row.cells.get(i).text();
                if (num(t) != null) {
                    open = new BigDecimal(num(t));
                    break;
                }
            }
            if (open == null) {
                open = pendingOpen;
            }
            pendingOpen = null;
            if (scheme != null && open != null) {
                scheme.open = trimNumber(open);
            }
        }

        private void takeClosing(String joined) {
            if (scheme != null) {
                Matcher cu = CLOSING_UNITS.matcher(joined);
                if (cu.find()) {
                    String v = num(cu.group(1));
                    if (v != null) {
                        scheme.close = trimNumber(new BigDecimal(v));
                    }
                }
                Matcher nav = NAV_LINE.matcher(joined);
                if (nav.find()) {
                    scheme.valuation.date = iso(nav.group(1));
                    scheme.valuation.nav = num(nav.group(2));
                }
                Matcher cost = COST_LINE.matcher(joined);
                if (cost.find()) {
                    scheme.valuation.cost = num(cost.group(1));
                }
                Matcher market = MARKET_LINE.matcher(joined);
                if (market.find()) {
                    if (scheme.valuation.date == null) {
                        scheme.valuation.date = iso(market.group(1));
                    }
                    scheme.valuation.value = num(market.group(2));
                }
            }
            scheme = null;
            pendingOpen = null;
            lastTxn = null;
        }

        // ------------------------------------------------------- ledger rows

        /** The row's own date: the lower layer of the date column (see class doc). */
        private LocalDate ledgerDate(Row row) {
            for (Cell c : row.cells) {
                if (c.x >= cTxn) {
                    continue;
                }
                for (int i = c.layers.size() - 1; i >= 0; i--) {
                    LocalDate d = date(c.layers.get(i).text.trim());
                    if (d != null) {
                        return d;
                    }
                }
            }
            return null;
        }

        private void takeLedgerRow(Row row, LocalDate date) {
            StringBuilder desc = new StringBuilder();
            String amount = null;
            String units = null;
            String nav = null;
            String balance = null;
            boolean hasNumber = false;
            for (Cell c : row.cells) {
                if (c.x >= cTxn && c.x < cAmount) {
                    String t = c.text();
                    if (!t.isBlank()) {
                        if (desc.length() > 0) {
                            desc.append(' ');
                        }
                        desc.append(t);
                    }
                    continue;
                }
                if (c.x < cAmount) {
                    continue;
                }
                String v = num(c.text());
                if (v == null) {
                    continue;
                }
                hasNumber = true;
                if (c.x < cUnits) {
                    amount = v;
                } else if (c.x < cPrice) {
                    units = v;
                } else if (c.x < cBalance) {
                    nav = v;
                } else {
                    balance = v;
                }
            }
            String text = collapse(desc.toString());
            if (text.isEmpty() && !hasNumber) {
                return; // a printed date with nothing behind it (page footer)
            }
            Double unitsValue = units == null ? null : Double.valueOf(units);
            TxnClassifier.Result kind = TxnClassifier.classify(text, unitsValue);

            KfCasReport.Txn txn = new KfCasReport.Txn();
            txn.date = date.toString();
            txn.description = text;
            txn.amount = amount;
            txn.units = units;
            txn.nav = nav;
            txn.balance = balance;
            txn.type = kind.type();
            txn.dividendRate = kind.dividendRate();
            txn.giftFolio = kind.giftFolio();
            scheme.transactions.add(txn);
            lastTxn = txn;
            lastTxnY = row.y;
        }

        /**
         * A statement row whose description is longer than the column is
         * printed on the lines below it (and the trailing reference number of a
         * redemption on its own line - even a lone "1" of "Instalment No 1").
         * Such a line carries no date and no column of its own and sits just
         * under the row it belongs to; the rotated page footer never reaches
         * here because those glyphs are dropped while reading (see
         * {@link Extractor#writeString}).
         */
        private boolean takeDescriptionTail(Row row) {
            if (row.y <= lastTxnY || row.y - lastTxnY > 15) {
                return false;
            }
            StringBuilder tail = new StringBuilder();
            for (Cell c : row.cells) {
                String t = c.text();
                if (!t.isBlank()) {
                    if (tail.length() > 0) {
                        tail.append(' ');
                    }
                    tail.append(t);
                }
            }
            String text = collapse(tail.toString());
            if (text.isEmpty()) {
                return false;
            }
            if (text.startsWith("Page ") || text.contains("KFINCASWS")) {
                return false;
            }
            lastTxn.description = collapse(lastTxn.description + " " + text);
            // the type is decided on the complete description
            Double units = lastTxn.units == null ? null : Double.valueOf(lastTxn.units);
            TxnClassifier.Result kind = TxnClassifier.classify(lastTxn.description, units);
            lastTxn.type = kind.type();
            lastTxn.dividendRate = kind.dividendRate();
            lastTxn.giftFolio = kind.giftFolio();
            lastTxnY = row.y;
            return true;
        }

        // ------------------------------------------------------------ helpers

        /** close_calculated = opening balance + the ledger's units (BigDecimal
         *  arithmetic keeps the printed precision of the widest operand). */
        private static String calcClose(KfCasReport.Scheme s) {
            BigDecimal total = s.open == null ? BigDecimal.ZERO : new BigDecimal(s.open);
            for (KfCasReport.Txn t : s.transactions) {
                if (t.units != null) {
                    total = total.add(new BigDecimal(t.units));
                }
            }
            return total.toPlainString();
        }

        private static boolean hasNomineeCell(Row row) {
            for (Cell c : row.cells) {
                if (NOMINEE_CELL.matcher(c.text()).find()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isAmcHeader(Row row) {
            if (row.cells.size() != 1) {
                return false;
            }
            Cell c = row.cells.get(0);
            if (c.x > 120) {
                return false;
            }
            String t = c.text();
            if (t.isEmpty() || t.length() > 60) {
                return false;
            }
            String l = t.toLowerCase(Locale.ROOT);
            if (!l.endsWith("mutual fund") && !l.endsWith(" mf")) {
                return false;
            }
            return !l.contains("folio") && !l.contains("value") && !l.contains("page")
                    && !l.contains("cost") && !l.contains("total") && !l.contains("period");
        }

        private static boolean containsCell(Row row, String needle) {
            for (Cell c : row.cells) {
                if (c.text().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
            return false;
        }

        private static Double cellX(Row row, String needle) {
            for (Cell c : row.cells) {
                if (c.text().toLowerCase(Locale.ROOT).contains(needle)) {
                    return c.x;
                }
            }
            return null;
        }

        private static String join(List<Cell> cells, java.util.function.Function<Cell, String> f) {
            StringBuilder sb = new StringBuilder();
            for (Cell c : cells) {
                String t = f.apply(c);
                if (t == null || t.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
            }
            return collapse(sb.toString());
        }
    }

    // --------------------------------------------------------------- utility

    /** Parses "16-Aug-2023" / "16-AUG-2023". */
    static LocalDate date(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = DATE_TEXT.matcher(raw.trim());
        if (!m.matches()) {
            return null;
        }
        int month = month(m.group(2));
        if (month == 0) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int month(String mon) {
        String[] months = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        for (int i = 0; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(mon)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String iso(String printed) {
        LocalDate d = date(printed);
        return d == null ? null : d.toString();
    }

    private static boolean isDate(String raw) {
        return date(raw) != null;
    }

    /**
     * Normalises a printed number: drops the thousands separators, turns the
     * accounting parentheses into a minus sign and returns null when the text
     * is not a number at all.
     */
    static String num(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(" ", "");
        boolean neg = s.startsWith("(") && s.endsWith(")");
        s = s.replace("(", "").replace(")", "").replace(",", "");
        if (s.isEmpty() || !NUMBER_TEXT.matcher(s).matches()) {
            return null;
        }
        return neg ? "-" + s : s;
    }

    /**
     * Keeps the printed scale, except that a value whose fraction is all
     * zeroes ("0.000") collapses to its integer form ("0") - the same way the
     * reference conversion renders balances.
     */
    private static String trimNumber(BigDecimal v) {
        String s = v.toPlainString();
        int dot = s.indexOf('.');
        if (dot >= 0) {
            String frac = s.substring(dot + 1);
            if (!frac.isEmpty() && frac.replace('0', ' ').trim().isEmpty()) {
                s = s.substring(0, dot);
            }
        }
        return s;
    }

    static String collapse(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
