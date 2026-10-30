package com.artech.portfolio.mf.util;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Identity of one ledger row across two uploads (MF.md 12a, MF_V3 2).
 *
 * <p>A transaction is the same transaction when it sits in the same scheme
 * (folio number and scheme name), on the same date, of the same type, with the
 * same description and the same numbers. Both the reconciliation reports and
 * accept build the key here, so a row staged twice can never be counted - or
 * stored - twice.</p>
 */
public final class MfTxnIdentity {

    private MfTxnIdentity() {
    }

    /** Identity of a scheme across two uploads: its folio and its own name. */
    public static String schemeKey(String folioNo, String scheme) {
        return (folioNo == null ? "" : folioNo) + " | " + (scheme == null ? "" : scheme);
    }

    public static String of(String schemeKey, LocalDate date, String type, String description,
                            BigDecimal amount, BigDecimal units) {
        return (schemeKey == null ? "" : schemeKey)
                + "|" + date
                + "|" + (type == null ? "" : type)
                + "|" + (description == null ? "" : description)
                + "|" + num(amount)
                + "|" + num(units);
    }

    /**
     * Numbers keep their value, not their scale: 1.10 and 1.1 are the same
     * amount, 1.1000000 and 1.1000001 are not.
     */
    private static String num(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString();
    }
}
