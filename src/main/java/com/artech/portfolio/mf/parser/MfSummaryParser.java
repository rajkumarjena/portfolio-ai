package com.artech.portfolio.mf.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coordinate-aware parser for the tabular KFIN/CAMS consolidated summary PDF. */
public final class MfSummaryParser {
    private static final Pattern AS_ON = Pattern.compile("(?i)\\bAs\\s+on\\s+(\\d{1,2}-[A-Za-z]{3}-\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("(?i)Email\\s*Id\\s*:\\s*([^\\s]+)");
    private static final Pattern NUM = Pattern.compile("^[+-]?(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.\\d+)?$");
    private static final DateTimeFormatter PRINTED_DATE = new java.time.format.DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH);
    private static final double ROW_GAP = 2.5;

    public MfSummaryReport parse(byte[] bytes, String password) {
        try (PDDocument document = Loader.loadPDF(bytes, password == null ? "" : password)) {
            Extractor extractor = new Extractor();
            extractor.setSortByPosition(true);
            extractor.writeText(document, new java.io.StringWriter());
            MfSummaryReport report = new MfSummaryReport();
            List<Row> rows = extractor.rows;
            String allText = rows.stream().map(Row::joined).reduce("", (a, b) -> a + "\n" + b);
            if (!allText.toLowerCase(Locale.ROOT).contains("consolidated account summary")) {
                throw new IllegalArgumentException("This is not a Consolidated Account Summary PDF.");
            }
            Matcher dateMatch = AS_ON.matcher(allText);
            if (!dateMatch.find()) throw new IllegalArgumentException("Summary statement is missing its 'As on' date.");
            report.asOn = parseDate(dateMatch.group(1));
            Matcher emailMatch = EMAIL.matcher(allText);
            if (!emailMatch.find()) throw new IllegalArgumentException("Summary statement is missing its Email Id.");
            report.email = emailMatch.group(1).trim().toLowerCase(Locale.ROOT);
            parseTable(rows, report);
            if (report.lines.isEmpty()) throw new IllegalArgumentException("No scheme rows could be read from the summary statement.");
            if (report.totalCost == null || report.totalMarket == null) {
                throw new IllegalArgumentException("The summary statement is missing its printed total cost or market value.");
            }
            return report;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new IllegalArgumentException(password == null || password.isBlank()
                    ? "Summary PDF is password protected; enter its password."
                    : "Summary PDF password is incorrect.");
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read the summary PDF: " + e.getMessage(), e);
        }
    }

    private static void parseTable(List<Row> rows, MfSummaryReport report) {
        boolean tableStarted = false;
        String folio = null;
        String isin = null;
        StringBuilder scheme = new StringBuilder();
        Pattern folioIsin = Pattern.compile("^\\s*(\\d{5,})\\s+(INF[A-Z0-9]{8,11})\\s*(.*)$", Pattern.CASE_INSENSITIVE);
        Pattern navDate = Pattern.compile("(?i)\\b(\\d{1,2}-[A-Za-z]{3}-\\d{4})\\b");
        Pattern numeric = Pattern.compile("(?<![A-Za-z0-9])([+-]?(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.\\d+)?)(?![A-Za-z0-9])");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            String joined = row.joined().trim();
            if (!tableStarted && containsIgnoreCase(joined, "Folio No") && containsIgnoreCase(joined, "Scheme Name")) {
                List<Cell> headerCells = new ArrayList<>();
                StringBuilder headerText = new StringBuilder();
                for (int previous = Math.max(0, rowIndex - 3); previous <= rowIndex; previous++) {
                    headerCells.addAll(rows.get(previous).cells);
                    headerText.append(' ').append(rows.get(previous).joined());
                }
                for (int next = rowIndex + 1; next < Math.min(rows.size(), rowIndex + 3); next++) {
                    headerCells.addAll(rows.get(next).cells);
                    headerText.append(' ').append(rows.get(next).joined());
                }
                if (containsIgnoreCase(headerText.toString(), "Cost Value") && containsIgnoreCase(headerText.toString(), "Market Value")) {
                    tableStarted = true;
                }
            }
            if (!tableStarted) continue;
            if (containsIgnoreCase(joined, "Total")) {
                BigDecimal[] totals = totalsFromRow(joined);
                if (totals[0] != null) report.totalCost = totals[0];
                if (totals[1] != null) report.totalMarket = totals[1];
                continue;
            }
            if (containsIgnoreCase(joined, "Folio No") || containsIgnoreCase(joined, "Consolidated Account Summary")) continue;

            Matcher id = folioIsin.matcher(joined);
            String valueText = joined;
            if (id.matches()) {
                folio = id.group(1);
                isin = id.group(2).toUpperCase(Locale.ROOT);
                valueText = id.group(3).trim();
                scheme.setLength(0);
            }
            Matcher dateMatch = navDate.matcher(valueText);
            if (!dateMatch.find()) {
                String text = removeAmounts(valueText, numeric).trim();
                if (!text.isBlank() && !text.matches("(?i)^(CAMS|KFINTECH|Page\\s+\\d+.*)$")) {
                    appendScheme(scheme, text);
                }
                continue;
            }
            if (folio == null || isin == null) continue;

            String beforeDate = valueText.substring(0, dateMatch.start()).trim();
            String afterDate = valueText.substring(dateMatch.end()).trim();
            String registrar = null;
            Matcher registrarMatch = Pattern.compile("(?i)\\b(CAMS|KFINTECH)\\b").matcher(afterDate);
            if (registrarMatch.find()) registrar = registrarMatch.group(1).toUpperCase(Locale.ROOT);
            afterDate = afterDate.replaceAll("(?i)\\b(CAMS|KFINTECH)\\b", " ").trim();

            List<String> beforeNumbers = numbers(beforeDate, numeric);
            List<String> afterNumbers = numbers(afterDate, numeric);
            if (beforeNumbers.size() < 2 || afterNumbers.size() < 2) continue;
            String namePart = removeAmounts(beforeDate, numeric).trim();
            if (!namePart.isBlank()) appendScheme(scheme, namePart);
            if (scheme.isEmpty()) continue;

            MfSummaryReport.Line line = new MfSummaryReport.Line();
            line.folio = folio;
            line.isin = isin;
            line.scheme = normalizeScheme(scheme.toString());
            line.cost = decimal(beforeNumbers.get(beforeNumbers.size() - 2));
            line.units = decimal(beforeNumbers.get(beforeNumbers.size() - 1));
            line.navDate = parseDate(dateMatch.group(1));
            line.price = decimal(afterNumbers.get(0));
            line.market = decimal(afterNumbers.get(1));
            line.registrar = registrar;
            report.lines.add(line);
            scheme.setLength(0);
        }
    }

    private static List<String> numbers(String text, Pattern pattern) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) values.add(matcher.group(1));
        return values;
    }

    private static String removeAmounts(String text, Pattern pattern) {
        String withoutNumbers = pattern.matcher(text).replaceAll(" ");
        return withoutNumbers.replaceAll("(?i)\\b(CAMS|KFINTECH)\\b", " ").replaceAll("\\s+", " ").trim();
    }

    private static void appendScheme(StringBuilder scheme, String text) {
        String value = text.replaceAll("(?i)\\b(As\\s+on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{4})\\b", " ")
                .replaceAll("\\s+", " ").trim();
        if (value.isBlank()) return;
        if (scheme.length() > 0 && scheme.substring(Math.max(0, scheme.length() - Math.min(30, scheme.length()))).contains(value)) return;
        if (scheme.length() > 0) scheme.append(' ');
        scheme.append(value);
    }

    private static BigDecimal[] totalsFromRow(String row) {
        Matcher matcher = Pattern.compile("(?i)\\bTotal\\b(.*)$").matcher(row);
        if (!matcher.find()) return new BigDecimal[]{null, null};
        List<String> values = numbers(matcher.group(1), Pattern.compile("(?<![A-Za-z0-9])([+-]?(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.\\d+)?)(?![A-Za-z0-9])"));
        if (values.size() < 2) return new BigDecimal[]{null, null};
        int n = values.size();
        return new BigDecimal[]{decimal(values.get(n - 2)), decimal(values.get(n - 1))};
    }

    /** midpoint boundaries derived from the column header labels. */
    private static double[] headerStarts(List<Cell> cells) {
        double folio = -1, isin = -1, scheme = -1, cost = -1, units = -1, navDate = -1, price = -1, market = -1, registrar = -1;
        for (Cell c : cells) {
            String t = c.text.toLowerCase(Locale.ROOT);
            if (t.contains("folio no")) folio = c.x;
            else if (t.equals("isin")) isin = c.x;
            else if (t.contains("scheme name")) scheme = c.x;
            else if (t.contains("cost value")) cost = c.x;
            else if (t.contains("closing unit")) units = c.x;
            else if (t.contains("nav date")) navDate = c.x;
            else if (t.equals("price")) price = c.x;
            else if (t.contains("market value")) market = c.x;
            else if (t.contains("registrar")) registrar = c.x;
        }
        if (folio < 0 || isin < 0 || scheme < 0 || cost < 0 || units < 0 || navDate < 0 || price < 0 || market < 0 || registrar < 0) return null;
        return new double[]{folio, (folio + isin) / 2, (isin + scheme) / 2, (scheme + cost) / 2,
                (cost + units) / 2, (units + navDate) / 2, (navDate + price) / 2,
                (price + market) / 2, (market + registrar) / 2};
    }

    private static boolean isNumber(String text) { return text != null && NUM.matcher(text).matches(); }
    private static BigDecimal decimal(String text) { return new BigDecimal(text.replace(",", "")); }
    private static LocalDate parseDate(String text) {
        try { return LocalDate.parse(text.trim(), PRINTED_DATE); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Invalid summary date: " + text, e); }
    }
    private static String normalizeScheme(String text) { return text.replaceAll("\\s+", " ").trim().replaceAll("\\s*\\((?:Non[- ]?Demat|Demat)\\)$", "").trim(); }
    private static boolean containsIgnoreCase(String text, String term) { return text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)); }

    private static final class Cell {
        final double x; final String text;
        Cell(double x, String text) { this.x = x; this.text = text; }
    }
    private static final class Row {
        final List<Cell> cells;
        Row(List<Cell> cells) { this.cells = cells; }
        String joined() { return cells.stream().map(c -> c.text).reduce("", (a,b) -> a + " " + b); }
    }
    private static final class Glyph { final double x,y,w; final String ch;
        Glyph(double x,double y,double w,String ch){this.x=x;this.y=y;this.w=w;this.ch=ch;} }
    private static final class Extractor extends PDFTextStripper {
        final List<Row> rows = new ArrayList<>();
        final List<Glyph> glyphs = new ArrayList<>();
        Extractor() throws IOException { super(); }
        @Override protected void startPage(PDPage page) throws IOException { flush(); super.startPage(page); }
        @Override protected void endPage(PDPage page) throws IOException { flush(); super.endPage(page); }
        private void flush() {
            if (glyphs.isEmpty()) return;
            glyphs.sort(Comparator.comparingDouble((Glyph g)->g.y).thenComparingDouble(g->g.x));
            int i=0;
            while(i<glyphs.size()) {
                double y=glyphs.get(i).y; List<Glyph> band=new ArrayList<>(); int j=i;
                while(j<glyphs.size() && glyphs.get(j).y-y<=ROW_GAP) band.add(glyphs.get(j++));
                band.sort(Comparator.comparingDouble(g->g.x));
                List<Cell> cells=new ArrayList<>(); StringBuilder text=new StringBuilder(); double lastEnd=-1, firstX=-1;
                for (Glyph g : band) {
                    if (text.length() > 0 && g.x - lastEnd > 8) {
                        cells.add(new Cell(firstX, text.toString().trim()));
                        text.setLength(0);
                        firstX = -1;
                    }
                    if (firstX < 0) firstX = g.x;
                    if(text.length()>0 && g.x-lastEnd>1.2 && !Character.isWhitespace(text.charAt(text.length()-1))) text.append(' ');
                    text.append(g.ch); lastEnd=Math.max(lastEnd,g.x+g.w);
                }
                if(text.length()>0) cells.add(new Cell(firstX,text.toString().trim()));
                rows.add(new Row(cells)); i=j;
            }
            glyphs.clear();
        }
        @Override protected void writeString(String text,List<TextPosition> positions) {
            for(TextPosition p:positions) if(Math.abs(p.getDir()%360f)<0.01f) glyphs.add(new Glyph(p.getXDirAdj(),p.getY(),p.getWidthDirAdj(),p.getUnicode()));
        }
    }
}
