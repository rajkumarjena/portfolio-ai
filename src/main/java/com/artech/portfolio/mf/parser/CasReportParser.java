package com.artech.portfolio.mf.parser;

import com.artech.portfolio.mf.util.MfConstants;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MF Central / CAMS CAS statements come as xlsx, csv or pdf (MF.md 14).
 * Each parser fills the same ParsedCasReport.
 */
public interface CasReportParser {

    ParsedCasReport parse(String fileName, byte[] data) throws Exception;

    /** File type for MfImportBatch.fileType, by extension. */
    static String detectType(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".xlsx") || n.endsWith(".xls")) return MfConstants.FILE_XLSX;
        if (n.endsWith(".pdf")) return MfConstants.FILE_PDF;
        return MfConstants.FILE_CSV;
    }

    // ------------------------------------------------------------------ dates

    Pattern CAS_DATE = Pattern.compile("^(\\d{1,2})[-/]([A-Za-z]{3})[-/](\\d{4})$");
    Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
    Pattern NUM_DMY = Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$");

    /** Parses 16-AUG-2023, 16/08/2023 and 2023-08-16. */
    default LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            Matcher m = CAS_DATE.matcher(s);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)),
                        month(m.group(2)), Integer.parseInt(m.group(1)));
            }
            m = ISO_DATE.matcher(s);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = NUM_DMY.matcher(s);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            }
            // last resort: 16-Aug-2023 with a full month name, or java-ish formats
            for (String f : new String[]{"dd-MMM-yyyy", "dd MMM yyyy", "MM/dd/yyyy"}) {
                try {
                    SimpleDateFormat fmt = new SimpleDateFormat(f, Locale.ENGLISH);
                    fmt.setLenient(false);
                    return new java.sql.Date(fmt.parse(s).getTime()).toLocalDate();
                } catch (ParseException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static int month(String mon) {
        String[] months = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        String m = mon.toUpperCase(Locale.ROOT);
        for (int i = 0; i < months.length; i++) {
            if (months[i].equals(m)) return i + 1;
        }
        throw new IllegalArgumentException("Bad month " + mon);
    }

    /** Parses money/units cells: handles 1,23,456.78, (500.00) negatives and blanks. */
    static double num(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        if (s.isEmpty() || "-".equals(s) || "--".equals(s)) return 0;
        boolean neg = s.startsWith("(") && s.endsWith(")");
        s = s.replace("(", "").replace(")", "")
                .replace(",", "").replace("₹", "").replace("Rs.", "").trim();
        if (s.isEmpty()) return 0;
        try {
            double v = new BigDecimal(s).doubleValue();
            return neg ? -v : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** True when the cell actually contains a number (for header/total detection). */
    static boolean looksNumeric(String raw) {
        if (raw == null) return false;
        String s = raw.trim().replace(",", "");
        if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1, s.length() - 1);
        if (s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
