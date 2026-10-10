package com.artech.portfolio.bond.controller;

import com.artech.portfolio.bond.repository.CashFlowDTO;
import com.artech.portfolio.bond.repository.CashFlowDataRepository;
import com.artech.portfolio.bond.service.CashFlowDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashflows")
public class CashFlowDataController {

    private final CashFlowDataService cashFlowDataService;

    public CashFlowDataController(CashFlowDataRepository cashFlowDataRepository,
                                  CashFlowDataService cashFlowDataService) {
        this.cashFlowDataService = cashFlowDataService;
    }

    // Get all cashflows
    @GetMapping
    public List<CashFlowDTO> getAllCashFlows() {
        return cashFlowDataService.getAllCashFlows();
    }

    // Get by year
    @GetMapping("/year/{year}")
    public List<CashFlowDTO> getByYear(@PathVariable int year) {
        return cashFlowDataService.getByYear(year);
    }

    // Get by exact date
    @GetMapping("/date/{date}")
    public List<CashFlowDTO> getByDate(@PathVariable String date) {
        return cashFlowDataService.getByDate(date);
    }

    // Get by month
    @GetMapping("/month/{monthStr}")
    public List<CashFlowDTO> getByMonth(@PathVariable String monthStr) {
        return cashFlowDataService.getByMonth(monthStr);
    }

    // Get by bond id.
    @GetMapping("/bond/{bondId}")
    public List<CashFlowDTO> getByBond(@PathVariable Long bondId) {
        return cashFlowDataService.getByBond(bondId);
    }

    // Get by date range
    @GetMapping("/range")
    public List<CashFlowDTO> getByDateRange(@RequestParam String startDate,
                                            @RequestParam String endDate) {
        return cashFlowDataService.getByDateRange(startDate, endDate);
    }
}
