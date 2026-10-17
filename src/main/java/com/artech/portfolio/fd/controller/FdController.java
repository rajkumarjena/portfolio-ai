package com.artech.portfolio.fd.controller;

import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.fd.entity.FixedDeposit;
import com.artech.portfolio.fd.service.FdService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fds")
public class FdController {

    private final FdService fdService;

    public FdController(FdService fdService) {
        this.fdService = fdService;
    }

    @PostMapping
    public FixedDeposit saveFixedDeposit(@RequestBody FixedDeposit fixedDeposit) {
        return fdService.saveFixedDeposit(fixedDeposit);
    }

    @GetMapping
    public List<FixedDeposit> getAllFixedDeposits() {
        return fdService.getAllFixedDeposits();
    }

    @PutMapping("/{id}")
    public FixedDeposit updateFixedDeposit(@PathVariable Long id,
                                           @RequestBody FixedDeposit fixedDepositDetails) {
        return fdService.updateFixedDeposit(id, fixedDepositDetails);
    }

    @DeleteMapping("/{id}")
    public void deleteFixedDeposit(@PathVariable Long id) {
        fdService.deleteFixedDeposit(id);
    }

    @GetMapping("/{id}")
    public FixedDeposit getFixedDepositById(@PathVariable Long id) {
        return fdService.getFixedDepositById(id);
    }

    // Bank names configured in the database (AppParamConfig), for the dropdown
    @GetMapping("/banks")
    public List<AppParamConfig> getBankNames() {
        return fdService.getBankNames();
    }

    /** Validation / not-found errors surface as a clean 400/404, never a 500. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("not found")
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
    }
}
