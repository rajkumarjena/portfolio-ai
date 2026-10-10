package com.artech.portfolio.bond.service;

import com.artech.portfolio.bond.repository.CashFlowDTO;

import java.util.List;

public interface CashFlowDataService {
    List<CashFlowDTO> getAllCashFlows();

    List<CashFlowDTO> getByYear(int year);

    List<CashFlowDTO> getByDate(String date);

    List<CashFlowDTO> getByMonth(String monthStr);

    List<CashFlowDTO> getByBond(Long bondId);

    List<CashFlowDTO> getByDateRange(String startDate, String endDate);
}
