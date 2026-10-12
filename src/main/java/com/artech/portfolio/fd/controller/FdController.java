package com.artech.portfolio.fd.controller;

import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.fd.entity.FixedDeposit;
import com.artech.portfolio.fd.service.FdService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
