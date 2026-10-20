package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.BondCashFlow;
import com.artech.portfolio.bond.repository.CashFlowDTO;
import com.artech.portfolio.bond.repository.CashFlowDataRepository;
import com.artech.portfolio.bond.service.CashFlowDataService;
import com.artech.portfolio.bond.util.DateUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CashFlowDataServiceImpl implements CashFlowDataService {
    private final CashFlowDataRepository cashFlowDataRepository;
    private final DateUtils dateUtils;
    private final CurrentUserService currentUserService;

    public CashFlowDataServiceImpl(CashFlowDataRepository cashFlowDataRepository,
                                   DateUtils dateUtils,
                                   CurrentUserService currentUserService) {
        this.cashFlowDataRepository = cashFlowDataRepository;
        this.dateUtils = dateUtils;
        this.currentUserService = currentUserService;
    }

    /**
     * BONDBP B.3: null means "no client filter" (ADMIN sees every client's
     * report); otherwise only cash flows of bonds owned by the signed-in
     * client leave the service. Enforced here so no endpoint can bypass it.
     */
    private Long clientIdOrNull() {
        return currentUserService.isAdmin() ? null : currentUserService.requireClient().getId();
    }

    @Override
    public List<CashFlowDTO> getAllCashFlows() {
        Long clientId = clientIdOrNull();
        // BONDBP_V4 #1: the Bond Returns Report is always ordered by payment
        // date ascending. Both paths are ordered in SQL: the scoped finder has
        // ORDER BY in the JPQL, and the ADMIN path gets the equivalent Sort.
        List<BondCashFlow> rows = clientId == null
                ? cashFlowDataRepository.findAll(Sort.by(Sort.Direction.ASC, "date", "id"))
                : cashFlowDataRepository.findAllForClient(clientId);
        return rows.stream().map(this::toDTO).toList();
    }

    @Override
    public List<CashFlowDTO> getByYear(int year) {
        Long clientId = clientIdOrNull();
        List<BondCashFlow> rows = clientId == null
                ? cashFlowDataRepository.findByYear(year)
                : cashFlowDataRepository.findByYearForClient(clientId, year);
        return rows.stream().map(this::toDTO).toList();
    }

    @Override
    public List<CashFlowDTO> getByDate(String date) {
        LocalDate localDate = dateUtils.parseToLocalDate(date);
        Long clientId = clientIdOrNull();
        List<BondCashFlow> rows = clientId == null
                ? cashFlowDataRepository.findByDate(localDate)
                : cashFlowDataRepository.findByDateForClient(clientId, localDate);
        return rows.stream().map(this::toDTO).toList();
    }

    @Override
    public List<CashFlowDTO> getByMonth(String monthStr) {
        LocalDate[] boundaries = dateUtils.convertMonthToBoundaries(monthStr);
        Long clientId = clientIdOrNull();
        List<BondCashFlow> rows = clientId == null
                ? cashFlowDataRepository.findByDateRange(boundaries[0], boundaries[1])
                : cashFlowDataRepository.findByDateRangeForClient(clientId,
                        boundaries[0], boundaries[1]);
        return rows.stream().map(this::toDTO).toList();
    }

    @Override
    public List<CashFlowDTO> getByBond(Long bondId) {
        Long clientId = clientIdOrNull();
        List<BondCashFlow> rows = clientId == null
                ? cashFlowDataRepository.findByBondId(bondId)
                : cashFlowDataRepository.findByBondForClient(clientId, bondId);
        return rows.stream().map(this::toDTO).toList();
    }

    @Override
    public List<CashFlowDTO> getByDateRange(String startDate, String endDate) {
        LocalDate startDt = dateUtils.parseToLocalDate(startDate);
        LocalDate endDt = dateUtils.parseToLocalDate(endDate);
        Long clientId = clientIdOrNull();
        List<BondCashFlow> rows = clientId == null
                ? cashFlowDataRepository.findByDateRange(startDt, endDt)
                : cashFlowDataRepository.findByDateRangeForClient(clientId, startDt, endDt);
        return rows.stream().map(this::toDTO).toList();
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
            dto.setIssuerId(cf.getBond().getIssuer()); // BONDBP B.2
            if (cf.getBond().getClient() != null) {
                dto.setClientName(cf.getBond().getClient().getName());
                dto.setClientPan(cf.getBond().getClient().getPan());
            }
        }
        return dto;
    }
}
