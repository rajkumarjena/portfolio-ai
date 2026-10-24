package com.artech.portfolio.fd.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.common.repository.AppParamRepository;
import com.artech.portfolio.fd.entity.FixedDeposit;
import com.artech.portfolio.fd.repository.FixedDepositRepository;
import com.artech.portfolio.fd.service.FdService;
import com.artech.portfolio.fd.util.FdCalculator;
import com.artech.portfolio.fd.util.FdConstants;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FdServiceImpl implements FdService {
    private final FixedDepositRepository fixedDepositRepository;
    private final AppParamRepository appParamRepository;
    private final CurrentUserService currentUserService;

    public FdServiceImpl(FixedDepositRepository fixedDepositRepository,
                         AppParamRepository appParamRepository,
                         CurrentUserService currentUserService) {
        this.fixedDepositRepository = fixedDepositRepository;
        this.appParamRepository = appParamRepository;
        this.currentUserService = currentUserService;
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
        // CONSISTENCY.md 6: the logged-in client owns the row; any client id in
        // the request body is ignored (reset() also detaches a lazy-loaded one).
        fixedDeposit.setId(null);
        fixedDeposit.setClient(currentUserService.requireClient());
        applyMaturityCalculation(fixedDeposit);
        return fixedDepositRepository.save(fixedDeposit);
    }

    @Override
    public List<FixedDeposit> getAllFixedDeposits() {
        // ADMIN sees the whole book for dashboard reporting; everyone else only
        // their own rows (CONSISTENCY.md 6).
        if (currentUserService.isAdmin()) {
            return fixedDepositRepository.findAll();
        }
        Client me = currentUserService.requireClient();
        return fixedDepositRepository.findByClientId(me.getId());
    }

    @Override
    public FixedDeposit getFixedDepositById(Long id) {
        return loadOwned(id)
                .orElseThrow(() -> new RuntimeException("Fixed deposit not found with id " + id));
    }

    @Override
    public FixedDeposit updateFixedDeposit(Long id, FixedDeposit fixedDepositDetails) {
        return loadOwned(id).map(fd -> {
            fd.setBankName(fixedDepositDetails.getBankName());
            fd.setDepositAmount(fixedDepositDetails.getDepositAmount());
            fd.setDepositDate(fixedDepositDetails.getDepositDate());
            fd.setMaturityDate(fixedDepositDetails.getMaturityDate());
            fd.setInterestRate(fixedDepositDetails.getInterestRate());
            fd.setMaturityAmount(fixedDepositDetails.getMaturityAmount());
            fd.setStatus(fixedDepositDetails.getStatus());
            fd.setClosureDate(fixedDepositDetails.getClosureDate());
            fd.setClosureAmount(fixedDepositDetails.getClosureAmount());
            fd.setClosureReason(fixedDepositDetails.getClosureReason());
            fd.setNotes(fixedDepositDetails.getNotes());
            // owner never changes on update

            validate(fd);
            applyMaturityCalculation(fd);
            return fixedDepositRepository.save(fd);
        }).orElseThrow(() -> new RuntimeException("Fixed deposit not found with id " + id));
    }

    @Override
    public void deleteFixedDeposit(Long id) {
        FixedDeposit fd = loadOwned(id)
                .orElseThrow(() -> new RuntimeException("Fixed deposit not found with id " + id));
        fixedDepositRepository.delete(fd);
    }

    @Override
    public List<AppParamConfig> getBankNames() {
        return appParamRepository
                .findByInvestmentTypeAndParamName(FdConstants.FD, FdConstants.BANK);
    }

    /**
     * Loads a deposit and enforces ownership (CONSISTENCY.md 6): a non-admin
     * user gets the "not found" outcome for anyone else's row so ids cannot be
     * probed across clients.
     */
    private java.util.Optional<FixedDeposit> loadOwned(Long id) {
        FixedDeposit fd = fixedDepositRepository.findById(id).orElse(null);
        if (fd == null) {
            return java.util.Optional.empty();
        }
        if (currentUserService.isAdmin()) {
            return java.util.Optional.of(fd);
        }
        Client me = currentUserService.requireClient();
        return fd.getClient() != null && fd.getClient().getId().equals(me.getId())
                ? java.util.Optional.of(fd) : java.util.Optional.empty();
    }

    private void validate(FixedDeposit fd) {
        if (fd.getDepositDate() != null && fd.getMaturityDate() != null
                && fd.getMaturityDate().isBefore(fd.getDepositDate())) {
            throw new RuntimeException("Maturity date must be on or after deposit date");
        }
    }

    /**
     * FD_V1 #2: the maturity amount is always recomputed server-side with the
     * Indian quarterly-compounding formula, so a client-supplied value can
     * never drift from the bank's actual calculation.
     */
    private void applyMaturityCalculation(FixedDeposit fd) {
        fd.setMaturityAmount(FdCalculator.maturityAmount(
                fd.getDepositAmount(), fd.getInterestRate(),
                fd.getDepositDate(), fd.getMaturityDate()));
    }
}
