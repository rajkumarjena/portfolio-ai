package com.artech.portfolio.fd.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * FD_V1 #2: Indian banking fixed-deposit maturity calculation.
 *
 * Indian banks compound interest QUARTERLY, so the maturity amount is
 *
 *     A = P * (1 + R / 400) ^ (4 * years)
 *
 * P = deposit amount, R = annual interest rate in percent, and the tenure in
 * years is derived from the deposit and maturity DATES (FD_V1 #1 removed the
 * redundant tenure field): actual days / 365.
 */
public final class FdCalculator {

    /** Indian convention: 365-day year and 4 compounding quarters per year. */
    private static final double DAYS_PER_YEAR = 365.0;
    private static final int QUARTERS_PER_YEAR = 4;

    private FdCalculator() {
    }

    /** Maturity amount rounded to 2 decimals (paise), never less than principal. */
    public static double maturityAmount(double principal, double annualRatePercent,
                                        LocalDate depositDate, LocalDate maturityDate) {
        if (depositDate == null || maturityDate == null || maturityDate.isBefore(depositDate)) {
            return principal;
        }
        double years = ChronoUnit.DAYS.between(depositDate, maturityDate) / DAYS_PER_YEAR;
        double growth = Math.pow(1 + annualRatePercent / (QUARTERS_PER_YEAR * 100),
                QUARTERS_PER_YEAR * years);
        return Math.round(principal * growth * 100.0) / 100.0;
    }

    /** Interest earned = maturity amount - principal, rounded to paise. */
    public static double interestEarned(double principal, double annualRatePercent,
                                        LocalDate depositDate, LocalDate maturityDate) {
        return Math.round((maturityAmount(principal, annualRatePercent, depositDate, maturityDate)
                - principal) * 100.0) / 100.0;
    }
}
