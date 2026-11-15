package com.artech.portfolio.bondsip.controller;

import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.dto.ReconcileBondSipRequest;
import com.artech.portfolio.bondsip.service.BondSipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BONDBP_V7/V9 + BOND_V11 Bond SIP REST API. All ownership and validation
 * rules live in the service; pages only render what they are given.
 * BONDBP_V9 removed the Bond SIP Transaction endpoints - instalments are bond
 * rows now, managed through /api/bonds.
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

    /** #1 + BOND_V11 #5: the Bond SIP screen list (clientIds = multi-select). */
    @GetMapping
    public List<BondSipView> listMine(@RequestParam(value = "clientId", required = false) Long clientId,
                                      @RequestParam(value = "clientIds", required = false) List<Long> clientIds,
                                      @RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        List<Long> ids = new ArrayList<>();
        if (clientIds != null) {
            ids.addAll(clientIds);
        }
        if (clientId != null) {
            ids.add(clientId);
        }
        return bondSipService.listMine(ids, all);
    }

    /** BONDBP_V9 #5: the screen's sync banner - "Bond SIP Transaction is OK ..." or the pending count. */
    @GetMapping("/sync-status")
    public Map<String, Object> syncStatus(@RequestParam(value = "clientIds", required = false) List<Long> clientIds) {
        return bondSipService.syncStatus(clientIds);
    }

    /** #4: manual execution trigger (same idempotent logic as the scheduler and the restart sync). */
    @PostMapping("/run-due")
    public Map<String, Integer> runDue() {
        return Map.of("executed", bondSipService.runDueSips());
    }

    /**
     * BONDBP_V9 #5 + BOND_V11 #2/#3/#4: manual Sync Now - create what is
     * missing for the signed-in client (or, for an ADMIN, for the selected
     * clients), then report the banner state.
     */
    @PostMapping("/reconcile")
    public Map<String, Object> reconcile(@RequestBody(required = false) ReconcileBondSipRequest request) {
        List<Long> clientIds = request == null || request.getClientIds() == null
                ? List.of() : request.getClientIds();
        int created = bondSipService.reconcileForCurrentUser(clientIds);
        Map<String, Object> status = new java.util.LinkedHashMap<>(bondSipService.syncStatus(clientIds));
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
