package com.artech.portfolio.mfsip.controller;

import com.artech.portfolio.mfsip.dto.MfBankOption;
import com.artech.portfolio.mfsip.dto.MfSipListResponse;
import com.artech.portfolio.mfsip.dto.MfSipRedeemRequest;
import com.artech.portfolio.mfsip.dto.MfSipRedeemResult;
import com.artech.portfolio.mfsip.dto.MfSipRequest;
import com.artech.portfolio.mfsip.dto.MfSipSchemeDto;
import com.artech.portfolio.mfsip.dto.MfSipStatusRequest;
import com.artech.portfolio.mfsip.dto.MfSipView;
import com.artech.portfolio.mfsip.service.MfSipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * MF.md 1/7/10/11/12 - the endpoints the existing Mutual Fund SIP screens call:
 * the entry screen, the SIP list, its pause/resume/cancel actions, the run-due
 * button and the redeem dialog. All rules live in the service.
 */
@RestController
@RequestMapping("/api/mf")
public class MfSipController {

    private final MfSipService mfSipService;

    public MfSipController(MfSipService mfSipService) {
        this.mfSipService = mfSipService;
    }

    @GetMapping("/sips")
    public MfSipListResponse list() {
        return mfSipService.list();
    }

    @GetMapping("/sips/{id}")
    public MfSipView get(@PathVariable Long id) {
        return mfSipService.get(id);
    }

    @PostMapping("/sips")
    public MfSipView create(@RequestBody MfSipRequest request) {
        return mfSipService.create(request);
    }

    @PutMapping("/sips/{id}")
    public MfSipView update(@PathVariable Long id, @RequestBody MfSipRequest request) {
        return mfSipService.update(id, request);
    }

    @PostMapping("/sips/{id}/status")
    public MfSipView changeStatus(@PathVariable Long id,
                                  @RequestBody MfSipStatusRequest request) {
        return mfSipService.changeStatus(id, request == null ? null : request.getStatus());
    }

    @PostMapping("/sips/{id}/redeem")
    public MfSipRedeemResult redeem(@PathVariable Long id,
                                    @RequestBody MfSipRedeemRequest request) {
        return mfSipService.redeem(id, request);
    }

    @PostMapping("/sips/run-due")
    public Map<String, Integer> runDue() {
        return Map.of("executed", mfSipService.runDueSips());
    }

    /** Datalist suggestions of the entry screen. */
    @GetMapping("/schemes")
    public List<MfSipSchemeDto> schemes() {
        return mfSipService.schemeOptions();
    }

    /** The client's bank accounts for the deduction drop-down. */
    @GetMapping("/banks")
    public List<MfBankOption> banks() {
        return mfSipService.bankOptions();
    }

    /** Validation / not-found errors surface as a clean 400/404, never a 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("not found")
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("message", ex.getMessage() == null ? "Request failed" : ex.getMessage()));
    }
}
