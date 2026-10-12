package com.artech.portfolio.fd.service.impl;

import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.common.repository.AppParamRepository;
import com.artech.portfolio.fd.entity.FixedDeposit;
import com.artech.portfolio.fd.repository.FixedDepositRepository;
import com.artech.portfolio.fd.service.FdService;
import com.artech.portfolio.fd.util.FdConstants;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FdServiceImpl implements FdService {
    private final FixedDepositRepository fixedDepositRepository;
    private final AppParamRepository appParamRepository;

    public FdServiceImpl(FixedDepositRepository fixedDepositRepository,
                         AppParamRepository appParamRepository) {
        this.fixedDepositRepository = fixedDepositRepository;
        this.appParamRepository = appParamRepository;
    }

    /**
     * Seeds a few default bank names into the AppParamConfig table on first
     * startup, so the bank dropdown auto-populates like the bond issuer list.
     * Existing rows are never overwritten - more banks can be added any time
     * from the "Application Parameter" screen.
     */
    @PostConstruct
    public void seedDefaultBankNames() {
        List<AppParamConfig> existing = appParamRepository
                .findByInvestmentTypeAndParamName(FdConstants.FD, FdConstants.BANK);
        if (!existing.isEmpty()) {
            return;
        }
        String[] defaultBanks = {
                "State Bank of India", "HDFC Bank", "ICICI Bank",
                "Axis Bank", "Punjab National Bank"
        };
        for (String bank : defaultBanks) {
            AppParamConfig config = new AppParamConfig();
            config.setInvestmentType(FdConstants.FD);
            config.setParamName(FdConstants.BANK);
            config.setParamValue(bank);
            config.setNote("Default seeded bank name");
            appParamRepository.save(config);
        }
    }

    @Override
    public FixedDeposit saveFixedDeposit(FixedDeposit fixedDeposit) {
        validate(fixedDeposit);
        if (fixedDeposit.getStatus() == null || fixedDeposit.getStatus().isBlank()) {
            fixedDeposit.setStatus(FdConstants.STATUS_ACTIVE);
        }
        return fixedDepositRepository.save(fixedDeposit);
    }

    @Override
    public List<FixedDeposit> getAllFixedDeposits() {
        return fixedDepositRepository.findAll();
    }

    @Override
    public FixedDeposit getFixedDepositById(Long id) {
        return fixedDepositRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fixed deposit not found with id " + id));
    }

    @Override
    public FixedDeposit updateFixedDeposit(Long id, FixedDeposit fixedDepositDetails) {
        return fixedDepositRepository.findById(id).map(fd -> {
            fd.setBankName(fixedDepositDetails.getBankName());
            fd.setDepositAmount(fixedDepositDetails.getDepositAmount());
            fd.setTenureMonths(fixedDepositDetails.getTenureMonths());
            fd.setDepositDate(fixedDepositDetails.getDepositDate());
            fd.setMaturityDate(fixedDepositDetails.getMaturityDate());
            fd.setInterestRate(fixedDepositDetails.getInterestRate());
            fd.setMaturityAmount(fixedDepositDetails.getMaturityAmount());
            fd.setStatus(fixedDepositDetails.getStatus());
            fd.setClosureDate(fixedDepositDetails.getClosureDate());
            fd.setClosureAmount(fixedDepositDetails.getClosureAmount());
            fd.setClosureReason(fixedDepositDetails.getClosureReason());
            fd.setNotes(fixedDepositDetails.getNotes());

            validate(fd);
            return fixedDepositRepository.save(fd);
        }).orElseThrow(() -> new RuntimeException("Fixed deposit not found with id " + id));
    }

    @Override
    public void deleteFixedDeposit(Long id) {
        fixedDepositRepository.deleteById(id);
    }

    @Override
    public List<AppParamConfig> getBankNames() {
        return appParamRepository
                .findByInvestmentTypeAndParamName(FdConstants.FD, FdConstants.BANK);
    }

    private void validate(FixedDeposit fd) {
        if (fd.getDepositDate() != null && fd.getMaturityDate() != null
                && fd.getMaturityDate().isBefore(fd.getDepositDate())) {
            throw new RuntimeException("Maturity date must be on or after deposit date");
        }
    }
}
