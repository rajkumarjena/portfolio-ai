package com.artech.portfolio.mf.controller;

import com.artech.portfolio.client.entity.ClientBankAccount;
import com.artech.portfolio.mf.entity.MfHeader;
import com.artech.portfolio.mf.entity.MfScheme;
import com.artech.portfolio.mf.repository.MfSchemeRepository;
import com.artech.portfolio.mf.service.MfService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Mutual fund REST endpoints (MF.md): SIP input/view, portfolio view with
 * search + summary, per-header detail report and FIFO redemption.
 * Every query is scoped to the logged-in client's PAN (MF.md 4).
 */
@RestController
@RequestMapping("/api/mf")
public class MfController {

    private final MfService mfService;
    private final MfSchemeRepository schemeRepository;

    public MfController(MfService mfService, MfSchemeRepository schemeRepository) {
        this.mfService = mfService;
        this.schemeRepository = schemeRepository;
    }

    // ------------------------------------------------------------------ banks

    /** Bank dropdown for the SIP input screen (MF.md 3). */
    @GetMapping("/banks")
    public List<ClientBankAccount> banks(Authentication authentication) {
        return mfService.getBankAccounts(me(authentication));
    }

    // ---------------------------------------------------------------- schemes

    @GetMapping("/schemes")
    public List<MfScheme> schemes() {
        return mfService.getSchemes();
    }

    @PostMapping("/schemes")
    public MfScheme saveScheme(@RequestBody MfScheme scheme) {
        return mfService.saveScheme(scheme);
    }

    /** Distinct values for the portfolio search filters (MF.md 19). */
    @GetMapping("/filters")
    public Map<String, List<String>> filters() {
        return Map.of(
                "amcs", schemeRepository.findDistinctAmcs(),
                "categories", schemeRepository.findDistinctCategories());
    }

    // ------------------------------------------------------------------- SIPs

    /** All SIP registrations of this client, with a summary bar (MF.md 1). */
    @GetMapping("/sips")
    public Map<String, Object> sips(Authentication authentication) {
        return mfService.getSips(me(authentication));
    }

    @PostMapping("/sips")
    public MfHeader createSip(@RequestBody MfHeader header, Authentication authentication) {
        return mfService.saveHeader(header, me(authentication));
    }

    @GetMapping("/sips/{id}")
    public MfHeader getSip(@PathVariable Long id, Authentication authentication) {
        return mfService.getHeader(id, me(authentication));
    }

    @PutMapping("/sips/{id}")
    public MfHeader updateSip(@PathVariable Long id, @RequestBody MfHeader details,
                              Authentication authentication) {
        return mfService.updateHeader(id, details, me(authentication));
    }

    @DeleteMapping("/sips/{id}")
    public ResponseEntity<Map<String, String>> deleteSip(@PathVariable Long id,
                                                         Authentication authentication) {
        mfService.deleteHeader(id, me(authentication));
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    /** Pause / resume / cancel an active SIP (MF.md 10). */
    @PostMapping("/sips/{id}/status")
    public MfHeader setStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
                              Authentication authentication) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new RuntimeException("status is required");
        }
        return mfService.setStatus(id, status, me(authentication));
    }

    /** Redemption by units, amount or full sell-off, FIFO oldest lot first (MF.md 10). */
    @PostMapping("/sips/{id}/redeem")
    public Map<String, Object> redeem(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                      Authentication authentication) {
        String mode = body.get("mode") == null ? null : String.valueOf(body.get("mode"));
        double value = body.get("value") == null ? 0
                : Double.parseDouble(String.valueOf(body.get("value")));
        return mfService.redeem(id, mode, value, me(authentication));
    }

    /** Manual trigger of the daily auto-execution job (MF.md 7). */
    @PostMapping("/sips/run-due")
    public Map<String, Object> runDue() {
        int run = mfService.runDueSips();
        return Map.of("executed", run);
    }

    // ------------------------------------------------------------ view screens

    /** Portfolio headers + summary, filtered per MF.md 18/19. */
    @GetMapping("/headers")
    public Map<String, Object> headers(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) String amc,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String q,
                                       Authentication authentication) {
        return mfService.searchHeaders(me(authentication), parseDate(from), parseDate(to),
                amc, category, status, q);
    }

    /** Detail report behind one header row (MF.md 17). */
    @GetMapping("/headers/{id}")
    public Map<String, Object> headerDetail(@PathVariable Long id, Authentication authentication) {
        return mfService.getHeaderDetail(id, me(authentication));
    }

    /** Review-all-investments transaction report (MF.md 19). */
    @GetMapping("/transactions")
    public Map<String, Object> transactions(@RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) String amc,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) Long headerId,
                                            @RequestParam(required = false) String q,
                                            Authentication authentication) {
        return mfService.searchTransactions(me(authentication), parseDate(from), parseDate(to),
                amc, category, headerId, q);
    }

    // ------------------------------------------------------------------ utils

    private static String me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Authentication required");
        }
        return authentication.getName();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            throw new RuntimeException("Invalid date '" + raw + "' - expected yyyy-MM-dd");
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
