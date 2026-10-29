package com.artech.portfolio.mf.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfSummaryParserTest {
    @Test
    void parsesProvidedConsolidatedSummaryPdf() throws Exception {
        Path sample = Path.of("consolidated_account_summary.pdf");
        if (!Files.exists(sample)) return;
        MfSummaryReport report = new MfSummaryParser().parse(Files.readAllBytes(sample), "Anmol$09");
        assertEquals("rajkumarjena@gmail.com", report.email);
        assertEquals("2026-10-27", report.asOn.toString());
        assertFalse(report.lines.isEmpty());
        assertNotNull(report.totalCost);
        assertNotNull(report.totalMarket);
        assertEquals(0, report.totalCost.compareTo(new java.math.BigDecimal("3294237.30")));
        assertEquals(0, report.totalMarket.compareTo(new java.math.BigDecimal("5314530.97")));
        java.math.BigDecimal parsedCost = report.lines.stream().map(line -> line.cost).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal parsedMarket = report.lines.stream().map(line -> line.market).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertTrue(withinTolerance(report.totalCost, parsedCost), "parsed scheme cost sum should tie to the printed total: " + parsedCost);
        assertTrue(withinTolerance(report.totalMarket, parsedMarket), "parsed scheme market sum should tie to the printed total: " + parsedMarket);
    }

    private static boolean withinTolerance(java.math.BigDecimal expected, java.math.BigDecimal actual) {
        return expected.subtract(actual).abs().compareTo(expected.abs().multiply(new java.math.BigDecimal("0.0001"))) <= 0;
    }
}
