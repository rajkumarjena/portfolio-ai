package com.artech.portfolio.mf.service;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative folio matching across RTA formatting differences. */
public final class MfFolioMatcher {
    private MfFolioMatcher() {}

    public static String canonical(String folio) {
        if (folio == null) return "";
        String normalized = Normalizer.normalize(folio, Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized;
    }

    /**
     * Match only when the shorter identifier is a complete prefix or suffix of
     * the longer one. A caller must still treat a match as provisional when it
     * is not an exact printed-number match.
     */
    public static boolean related(String left, String right) {
        String a = canonical(left);
        String b = canonical(right);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        String shortValue = a.length() < b.length() ? a : b;
        String longValue = a.length() < b.length() ? b : a;
        if (!longValue.startsWith(shortValue) && !longValue.endsWith(shortValue)) return false;

        String rawA = raw(left);
        String rawB = raw(right);
        String shorter = rawA.length() <= rawB.length() ? rawA : rawB;
        String longer = rawA.length() <= rawB.length() ? rawB : rawA;
        if (longer.startsWith(shorter) && longer.length() > shorter.length()) {
            return separator(longer.charAt(shorter.length()));
        }
        if (longer.endsWith(shorter) && longer.length() > shorter.length()) {
            return separator(longer.charAt(longer.length() - shorter.length() - 1));
        }
        return false;
    }

    private static String raw(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim().toUpperCase(Locale.ROOT);
    }

    private static boolean separator(char value) {
        return !Character.isLetterOrDigit(value);
    }
}
