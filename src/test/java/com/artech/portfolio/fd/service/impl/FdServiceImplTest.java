package com.artech.portfolio.fd.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.common.repository.AppParamRepository;
import com.artech.portfolio.fd.entity.FixedDeposit;
import com.artech.portfolio.fd.repository.FixedDepositRepository;
import com.artech.portfolio.fd.util.FdCalculator;
import com.artech.portfolio.fd.util.FdConstants;
import com.artech.portfolio.common.entity.AppParamConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FD_V1 #2: the Mature Amount stored by the server is always the Indian
 * quarterly-compounding result, not whatever the client sent.
 */
class FdServiceImplTest {

    private FixedDepositRepository fixedDepositRepository;
    private AppParamRepository appParamRepository;
    private CurrentUserService currentUserService;
    private ClientRepository clientRepository;
    private FdServiceImpl fdService;
    private Client me;

    @BeforeEach
    void setUp() {
        fixedDepositRepository = mock(FixedDepositRepository.class);
        appParamRepository = mock(AppParamRepository.class);
        currentUserService = mock(CurrentUserService.class);
        clientRepository = mock(ClientRepository.class);
        fdService = new FdServiceImpl(fixedDepositRepository,
                appParamRepository, currentUserService, clientRepository);

        me = new Client();
        me.setId(1L);
        me.setName("Ramesh Kumar");
        when(currentUserService.requireClient()).thenReturn(me);
    }

    @Test
    void saveRecomputesMaturityAmountWithQuarterlyCompounding() {
        FixedDeposit fd = new FixedDeposit();
        fd.setBankName("HDFC Bank");
        fd.setDepositAmount(100000);
        fd.setInterestRate(7);
        fd.setDepositDate(LocalDate.of(2024, 1, 1));
        fd.setMaturityDate(LocalDate.of(2025, 1, 1));
        fd.setMaturityAmount(99999); // wrong client-supplied value must be overwritten
        when(fixedDepositRepository.save(any(FixedDeposit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FixedDeposit saved = fdService.saveFixedDeposit(fd);

        assertEquals(FdCalculator.maturityAmount(100000, 7,
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)),
                saved.getMaturityAmount());
        assertEquals(107206.28, saved.getMaturityAmount(), 0.01);
    }

    @Test
    void updateRecomputesMaturityAmountAfterEdits() {
        FixedDeposit existing = new FixedDeposit();
        existing.setId(5L);
        existing.setClient(me);
        existing.setDepositAmount(50000);
        existing.setInterestRate(6);
        existing.setDepositDate(LocalDate.of(2024, 1, 1));
        existing.setMaturityDate(LocalDate.of(2025, 1, 1));
        when(fixedDepositRepository.findById(5L))
                .thenReturn(java.util.Optional.of(existing));
        when(fixedDepositRepository.save(any(FixedDeposit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FixedDeposit incoming = new FixedDeposit();
        incoming.setBankName("ICICI Bank");
        incoming.setDepositAmount(100000);
        incoming.setInterestRate(7);
        incoming.setDepositDate(LocalDate.of(2024, 1, 1));
        incoming.setMaturityDate(LocalDate.of(2025, 1, 1));
        incoming.setMaturityAmount(1); // stale/attacker-supplied value

        FixedDeposit updated = fdService.updateFixedDeposit(5L, incoming);

        assertEquals(107206.28, updated.getMaturityAmount(), 0.01);
    }

    // --------------------------------------- ADMIN_V6 #3 seeded rows carry a client_id

    @Test
    void seedDefaultBankNamesAttachesTheAdminOwnerClientId() {
        Client admin = new Client();
        admin.setId(9L);
        admin.setRole("ADMIN");
        when(appParamRepository
                .findByInvestmentTypeAndParamName(FdConstants.FD, FdConstants.BANK))
                .thenReturn(List.of());
        when(clientRepository.findByRole("ADMIN")).thenReturn(List.of(admin));
        when(appParamRepository.save(any(AppParamConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        fdService.seedDefaultBankNames();

        verify(appParamRepository, org.mockito.Mockito.times(5))
                .save(any(AppParamConfig.class));
        org.mockito.ArgumentCaptor<AppParamConfig> captor =
                org.mockito.ArgumentCaptor.forClass(AppParamConfig.class);
        verify(appParamRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        captor.getAllValues().forEach(row ->
                assertEquals(9L, row.getClientId(),
                        "ADMIN_V6 #3: seeded rows must carry a mandatory client_id"));
    }

    @Test
    void seedSkipsWhenNoClientExistsYetToOwnTheMandatoryClientId() {
        when(appParamRepository
                .findByInvestmentTypeAndParamName(FdConstants.FD, FdConstants.BANK))
                .thenReturn(List.of());
        when(clientRepository.findByRole("ADMIN")).thenReturn(List.of());

        fdService.seedDefaultBankNames();

        verify(appParamRepository, never()).save(any(AppParamConfig.class));
    }
}
