package com.artech.portfolio.mf.controller;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.mf.service.MfPortfolioReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * The mutual fund report pages (MFR_V8 / MFR_V9):
 *
 * <ul>
 *   <li>{@code GET /api/mf/report/portfolio} - AMC level cost / market value,
 *       with the optional {@code from} / {@code to} range and {@code amcId};</li>
 *   <li>{@code GET /api/mf/report/portfolio/transactions} - every accepted
 *       ledger row, filtered and paged 50 rows at a time;</li>
 *   <li>{@code GET /api/mf/report/portfolio/{amcId}} - one AMC's schemes and
 *       its ledger, with the same filters and paging.</li>
 * </ul>
 *
 * Every method is scoped to the signed-in client, so the request never carries
 * an owner id (MFR_V8). All of the reading and arithmetic lives in
 * {@link MfPortfolioReportService}.
 */
@RestController
@RequestMapping("/api/mf/report/portfolio")
public class MfPortfolioReportController {

    private final CurrentUserService currentUser;
    private final MfPortfolioReportService report;

    public MfPortfolioReportController(CurrentUserService currentUser,
            MfPortfolioReportService report) {
        this.currentUser = currentUser;
        this.report = report;
    }

    @GetMapping
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long amcId) {
        return report.summary(ownerPan(), from, to, amcId);
    }

    @GetMapping("/transactions")
    public Map<String, Object> transactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long amcId,
            @RequestParam(required = false) String scheme,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return report.transactions(ownerPan(), from, to, amcId, scheme, search, page, size);
    }

    @GetMapping("/{amcId}")
    public Map<String, Object> detail(@PathVariable Long amcId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String scheme,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return report.detail(ownerPan(), amcId, from, to, scheme, page, size);
    }

    private String ownerPan() {
        return currentUser.requireClient().getPan();
    }
}
