package com.artech.portfolio.fd.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FD_V1 #2: Indian banking fixed-deposit calculation - quarterly compounding
 * over the actual days between the deposit and maturity dates.
 */
class FdCalculatorTest {

    private static final LocalDate DEPOSIT = LocalDate.of(2024, 1, 1);
    private static final LocalDate MATURITY = LocalDate.of(2025, 1, 1);

    @Test
    void oneYearHundredThousandAtSevenPercentCompoundsQuarterly() {
        // 366 days in 2024 (leap year): 100000 * (1 + 7/400)^(4 * 366/365)
        double maturity = FdCalculator.maturityAmount(100000, 7, DEPOSIT, MATURITY);

        assertEquals(107206.28, maturity, 0.01);
    }

    @Test
    void quarterlyCompoundingBeatsSimpleInterest() {
        // Simple interest for a year would be exactly 7000 - compounding must
        // earn MORE (that is the whole point of the FD_V1 #2 rule).
        double earned = FdCalculator.interestEarned(100000, 7, DEPOSIT, MATURITY);

        assertEquals(7206.28, earned, 0.01);
    }

    @Test
    void partialYearUsesActualDaysOver365() {
        // 91 days: 100000 * (1 + 7/400)^(4 * 91/365)
        double maturity = FdCalculator.maturityAmount(100000, 7,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 4, 1));

        assertEquals(101745.16, maturity, 0.01);
    }

    @Test
    void zeroRateMaturesToPrincipal() {
        assertEquals(50000.0,
                FdCalculator.maturityAmount(50000, 0, DEPOSIT, MATURITY), 0.01);
    }

    @Test
    void invalidOrMissingDatesFallBackToPrincipal() {
        assertEquals(100000.0,
                FdCalculator.maturityAmount(100000, 7, DEPOSIT, null), 0.01);
        assertEquals(100000.0,
                FdCalculator.maturityAmount(100000, 7, MATURITY, DEPOSIT), 0.01);
    }
}
