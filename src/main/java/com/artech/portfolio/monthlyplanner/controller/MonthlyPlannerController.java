package com.artech.portfolio.monthlyplanner.controller;

import com.artech.portfolio.monthlyplanner.dto.PlannerHistoryRowView;
import com.artech.portfolio.monthlyplanner.dto.PlannerMonthView;
import com.artech.portfolio.monthlyplanner.dto.SavePlannerRequest;
import com.artech.portfolio.monthlyplanner.service.MonthlyPlannerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * MP_V1 - Monthly Planner REST API. Ownership and every calculation live in the
 * service; the pages only render what they are given.
 */
@RestController
@RequestMapping("/api/planner")
public class MonthlyPlannerController {

    private final MonthlyPlannerService plannerService;

    public MonthlyPlannerController(MonthlyPlannerService plannerService) {
        this.plannerService = plannerService;
    }

    /** One plan month (?month=yyyy-MM, current month when omitted). */
    @GetMapping
    public PlannerMonthView month(@RequestParam(value = "month", required = false) String month) {
        return plannerService.month(parse(month));
    }

    /** MP_V1 #2: the Cash-Flow Report - this month plus the configured next ones. */
    @GetMapping("/cashflow")
    public List<PlannerMonthView> cashFlow(@RequestParam(value = "month", required = false) String month) {
        return plannerService.cashFlow(parse(month));
    }

    /** The Save action of the Earnings / Spending screen. */
    @PutMapping("/entries")
    public PlannerMonthView save(@RequestBody SavePlannerRequest request) {
        return plannerService.save(request);
    }

    /**
     * MP_V1 #8: every captured month of a window (?from=yyyy-MM&amp;to=yyyy-MM;
     * the last twelve months when omitted) - the history the analytics will read.
     */
    @GetMapping("/history")
    public List<PlannerHistoryRowView> history(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return plannerService.history(parse(from), parse(to));
    }

    /** "yyyy-MM" (or a full date) to its first day; null means "the current month". */
    private java.time.LocalDate parse(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String value = month.trim();
        try {
            return value.length() == 7
                    ? java.time.YearMonth.parse(value).atDay(1)
                    : java.time.LocalDate.parse(value).withDayOfMonth(1);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Month must be in the yyyy-MM format");
        }
    }

    /** Validation errors (bad month, unknown category, negative amount) surface as a clean 400. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() == null ? "Request failed" : ex.getMessage()));
    }
}
