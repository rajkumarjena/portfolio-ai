package com.artech.portfolio.bondsip.controller;

import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.service.BondSipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * BONDBP_V7/V9 Bond SIP REST API. All ownership and validation rules live in
 * the service; pages only render what they are given. BONDBP_V9 removed the
 * Bond SIP Transaction endpoints - instalments are bond rows now, managed
 * through /api/bonds.
 */
@RestController
@RequestMapping("/api/bond-sips")
public class BondSipController {

    private final BondSipService bondSipService;

    public BondSipController(BondSipService bondSipService) {
        this.bondSipService = bondSipService;
    }

    /** #1/#2: create a Bond SIP for the signed-in client. */
    @PostMapping
    public BondSipView create(@RequestBody CreateBondSipRequest request) {
        return bondSipService.create(request);
    }

    /** #1: the lean Bond SIP screen list. */
    @GetMapping
    public List<BondSipView> listMine(@RequestParam(value = "clientId", required = false) Long clientId,
                                      @RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        return bondSipService.listMine(clientId, all);
    }

    /** BONDBP_V9 #5: the screen's sync banner - "Bond SIP Transaction is OK ..." or the pending count. */
    @GetMapping("/sync-status")
    public Map<String, Object> syncStatus() {
        return bondSipService.syncStatus();
    }

    /** #4: manual execution trigger (same idempotent logic as the scheduler and the restart sync). */
    @PostMapping("/run-due")
    public Map<String, Integer> runDue() {
        return Map.of("executed", bondSipService.runDueSips());
    }

    /** BONDBP_V9 #5 + V10 #1: manual Sync Now - create what is missing, then
     * report the banner state. ADMIN-only. */
    @PostMapping("/reconcile")
    public Map<String, Object> reconcile() {
        int created = bondSipService.reconcileForCurrentUser();
        Map<String, Object> status = new java.util.LinkedHashMap<>(bondSipService.syncStatus());
        status.put("created", created);
        return status;
    }

    /** BONDBP_V8 #5: cancel a Bond SIP at any time. */
    @PostMapping("/{id}/cancel")
    public BondSipView cancel(@PathVariable Long id) {
        return bondSipService.cancelSip(id);
    }

    /** Validation / not-found errors surface as a clean 400/404/409, never a 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("not found")
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
    }
}
