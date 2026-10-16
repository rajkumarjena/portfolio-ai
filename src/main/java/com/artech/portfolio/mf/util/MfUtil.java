package com.artech.portfolio.mf.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Small helpers shared by the SIP scheduler, redemption and import services.
 */
public final class MfUtil {

    private MfUtil() {
    }

    /** Normalises a numeric for fingerprints so 1.0 and 1 map to the same key. */
    public static String num(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Fingerprint for the no-duplicate guarantee (MF.md 22): identical
     * statements imported twice (or an SIP instalment that was already
     * auto-executed) produce the same key.
     */
    public static String dedupeKey(String pan, String schemeName, String amc, String folio,
                                   LocalDate date, String txnType, double units, double amount) {
        return String.join("|",
                norm(pan), norm(schemeName), norm(amc), norm(folio),
                date == null ? "" : date.toString(),
                norm(txnType), num(units), num(amount));
    }

    public static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /** Units are quoted with 3-4 decimals; keep reports tidy. */
    public static double roundUnits(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
