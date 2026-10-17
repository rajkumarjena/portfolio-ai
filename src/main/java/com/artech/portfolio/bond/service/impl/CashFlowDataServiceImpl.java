package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.bond.entity.BondCashFlow;
import com.artech.portfolio.bond.repository.CashFlowDTO;
import com.artech.portfolio.bond.repository.CashFlowDataRepository;
import com.artech.portfolio.bond.service.CashFlowDataService;
import com.artech.portfolio.bond.util.DateUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CashFlowDataServiceImpl implements CashFlowDataService {
    private final CashFlowDataRepository cashFlowDataRepository;
    private final DateUtils dateUtils;

    public CashFlowDataServiceImpl(CashFlowDataRepository cashFlowDataRepository, DateUtils dateUtils) {
        this.cashFlowDataRepository = cashFlowDataRepository;
        this.dateUtils = dateUtils;
    }

    @Override
    public List<CashFlowDTO> getAllCashFlows() {
        return cashFlowDataRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<CashFlowDTO> getByYear(int year) {
        return cashFlowDataRepository.findByYear(year).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<CashFlowDTO> getByDate(String date) {
        LocalDate localDate = dateUtils.parseToLocalDate(date);
        return cashFlowDataRepository.findByDate(localDate).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<CashFlowDTO> getByMonth(String monthStr) {
        LocalDate[] boundaries = dateUtils.convertMonthToBoundaries(monthStr);
        return cashFlowDataRepository.findByDateRange(boundaries[0], boundaries[1]).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<CashFlowDTO> getByBond(Long bondId) {
        return cashFlowDataRepository.findByBondId(bondId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<CashFlowDTO> getByDateRange(String startDate, String endDate) {
        LocalDate startDt = dateUtils.parseToLocalDate(startDate);
        LocalDate endDt = dateUtils.parseToLocalDate(endDate);
        return cashFlowDataRepository.findByDateRange(startDt, endDt).stream()
                .map(this::toDTO)
                .toList();
    }

    private CashFlowDTO toDTO(BondCashFlow cf) {
        CashFlowDTO dto = new CashFlowDTO();
        dto.setId(cf.getId());
        dto.setAmount(cf.getAmount());
        dto.setDate(cf.getDate());
        dto.setPayout(cf.getPayout());
        dto.setSchedule(cf.getSchedule());
        dto.setValuePct(cf.getValuePct());
        dto.setYear(cf.getYear());
        if (cf.getBond() != null) {
            dto.setBondId(cf.getBond().getId());
            dto.setBondName(cf.getBond().getBondName());
        }
        return dto;
    }
}
