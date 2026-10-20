package com.artech.portfolio.bondsip.controller;

import com.artech.portfolio.bondsip.dto.BondSipBondOption;
import com.artech.portfolio.bondsip.dto.BondSipTransactionView;
import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.dto.UpdateBondSipTransactionRequest;
import com.artech.portfolio.bondsip.service.BondSipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * BONDBP_V7 Bond SIP REST API. All ownership and validation rules live in the
 * service; pages only render what they are given.
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

    /** #1: the Bond SIP screen list. */
    @GetMapping
    public List<BondSipView> listMine() {
        return bondSipService.listMine();
    }

    /** #5: the Bond SIP Transaction screen list. */
    @GetMapping("/transactions")
    public List<BondSipTransactionView> listTransactions() {
        return bondSipService.listTransactions();
    }

    /** #6: Bond Name drop-down entries "Bond Name|PAN" (own + family bonds). */
    @GetMapping("/bond-options")
    public List<BondSipBondOption> bondOptions() {
        return bondSipService.bondOptions();
    }

    /** #5/#7: enter Actual SIP Amount + associate a Bond Name -> EXECUTED. */
    @PutMapping("/transactions/{id}")
    public BondSipTransactionView updateTransaction(@PathVariable Long id,
                                                    @RequestBody UpdateBondSipTransactionRequest request) {
        return bondSipService.updateTransaction(id, request);
    }

    /** #4: manual execution trigger (same logic as the daily scheduler). */
    @PostMapping("/run-due")
    public Map<String, Integer> runDue() {
        return Map.of("executed", bondSipService.runDueSips());
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
