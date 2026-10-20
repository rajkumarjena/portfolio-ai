package com.artech.portfolio.summary.controller;

import com.artech.portfolio.summary.dto.BondWidgetView;
import com.artech.portfolio.summary.service.PortfolioSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * BONDBP_V6 #1/#2: the portfolio-summary widget strip on top of the dashboard.
 * Today only the Bonds card (#1); the endpoint is namespaced per widget so the
 * upcoming FD / Shares / Mutual Funds cards land as siblings, not as one
 * ever-growing payload.
 */
@RestController
@RequestMapping("/api/portfolio/summary")
public class PortfolioSummaryController {

    private final PortfolioSummaryService portfolioSummaryService;

    public PortfolioSummaryController(PortfolioSummaryService portfolioSummaryService) {
        this.portfolioSummaryService = portfolioSummaryService;
    }

    /** The Bonds widget: rows + Self/Family figures, fully computed server-side (#6). */
    @GetMapping("/bonds")
    public BondWidgetView bonds() {
        return portfolioSummaryService.getBondWidget();
    }

    /** Clean 401/400 with {"message": ...} instead of a container error page. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }
}
