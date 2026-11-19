package com.artech.portfolio.mfsip.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mfsip.dto.MfSipListResponse;
import com.artech.portfolio.mfsip.dto.MfSipRedeemRequest;
import com.artech.portfolio.mfsip.dto.MfSipRedeemResult;
import com.artech.portfolio.mfsip.dto.MfSipRequest;
import com.artech.portfolio.mfsip.dto.MfSipSchemeDto;
import com.artech.portfolio.mfsip.dto.MfSipView;
import com.artech.portfolio.mfsip.entity.MfSipRegistration;
import com.artech.portfolio.mfsip.repository.MfSipRegistrationRepository;
import com.artech.portfolio.mfsip.util.MfSipConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MF.md 1/7/10/11/12 - the Mutual Fund SIP registration: create/update
 * validation, idempotent instalment execution, ownership, status changes and
 * redemption.
 */
class MfSipServiceImplTest {

    private static final LocalDate NOVEMBER_15 = LocalDate.of(2026, 11, 15);

    private MfSipRegistrationRepository repository;
    private MfFolioSchemeRepository schemeRepository;
    private CurrentUserService currentUserService;
    private MfSipServiceImpl service;

    private Client owner;
    private Client stranger;
    private final List<MfSipRegistration> rows = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        repository = mock(MfSipRegistrationRepository.class);
        schemeRepository = mock(MfFolioSchemeRepository.class);
        currentUserService = mock(CurrentUserService.class);

        owner = new Client();
        owner.setId(9L);
        owner.setPan("OWNER0001A");
        owner.setName("Owner");
        stranger = new Client();
        stranger.setId(10L);
        stranger.setPan("OTHER1111A");
        stranger.setName("Stranger");

        when(currentUserService.requireClient()).thenReturn(owner);
        when(schemeRepository.findByOwnerPan(anyString())).thenReturn(List.of());
        when(repository.save(any(MfSipRegistration.class))).thenAnswer(inv -> {
            MfSipRegistration row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(nextId++);
            }
            rows.removeIf(existing -> Objects.equals(existing.getId(), row.getId()));
            rows.add(row);
            return row;
        });
        when(repository.findById(any())).thenAnswer(inv ->
                rows.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());
        when(repository.findByClientIdOrderByIdAsc(any())).thenAnswer(inv -> rows.stream()
                .filter(r -> r.getClient() != null && r.getClient().getId().equals(inv.getArgument(0)))
                .toList());
        when(repository.findByStatusAndRegistrationType(anyString(), anyString())).thenAnswer(inv -> rows.stream()
                .filter(r -> inv.getArgument(0).equals(r.getStatus())
                        && inv.getArgument(1).equals(r.getRegistrationType()))
                .toList());

        Clock clock = Clock.fixed(NOVEMBER_15.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new MfSipServiceImpl(repository, schemeRepository, currentUserService, clock);
    }

    // ---------------------------------------------------------------- fixtures

    private MfSipRequest request(String scheme, String type, Double amount,
                                 LocalDate start, LocalDate end) {
        MfSipRequest request = new MfSipRequest();
        request.setScheme(MfSipSchemeDto.of(scheme, "PPFAS Mutual Fund", "Equity - Flexi Cap"));
        request.setRegistrationType(type);
        request.setSipType(MfSipConstants.MONTHLY);
        request.setSipAmount(amount);
        request.setSipDate(15);
        request.setSipStartDate(start);
        request.setSipEndDate(end);
        request.setStatus(MfSipConstants.STATUS_ACTIVE);
        return request;
    }

    private MfSipView createMonthly(double amount, LocalDate start) {
        return service.create(request("Parag Parikh Flexi Cap Fund", MfSipConstants.TYPE_SIP,
                amount, start, null));
    }

    // ------------------------------------------------------------ #1 #11 create

    @Test
    void createStoresTheRegistrationForTheSignedInClient() {
        MfSipView view = createMonthly(5000, LocalDate.of(2026, 1, 15));

        assertEquals("Parag Parikh Flexi Cap Fund", view.getScheme().getSchemeName());
        assertEquals("PPFAS Mutual Fund", view.getScheme().getAmcName());
        assertEquals(MfSipConstants.TYPE_SIP, view.getRegistrationType());
        assertEquals(MfSipConstants.MONTHLY, view.getSipType());
        assertEquals(5000, view.getSipAmount());
        assertEquals(15, view.getSipDate());
        assertEquals(MfSipConstants.STATUS_ACTIVE, view.getStatus());
        assertEquals(owner.getId(), rows.get(0).getClient().getId(),
                "ownership comes from the session");
    }

    @Test
    void createRejectsMissingSchemeZeroAmountAndBadWindow() {
        ResponseStatusException noScheme = assertThrows(ResponseStatusException.class,
                () -> service.create(request("  ", MfSipConstants.TYPE_SIP, 5000.0,
                        LocalDate.of(2026, 1, 1), null)));
        assertEquals(HttpStatus.BAD_REQUEST, noScheme.getStatusCode());

        ResponseStatusException noAmount = assertThrows(ResponseStatusException.class,
                () -> service.create(request("HDFC Equity", MfSipConstants.TYPE_SIP, 0.0,
                        LocalDate.of(2026, 1, 1), null)));
        assertTrue(noAmount.getReason().contains("greater than 0"));

        ResponseStatusException badWindow = assertThrows(ResponseStatusException.class,
                () -> service.create(request("HDFC Equity", MfSipConstants.TYPE_SIP, 500.0,
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))));
        assertTrue(badWindow.getReason().contains("End Date"));
    }

    // -------------------------------------------------------------- #7 run due

    @Test
    void runDueExecutesEveryDueInstalmentExactlyOnce() {
        MfSipView created = createMonthly(5000, LocalDate.of(2026, 9, 15));
        MfSipRegistration registration = rows.get(0);
        registration.setNav(50);

        // Sep 15, Oct 15 and Nov 15 are due by the fixed "today" (15 Nov 2026).
        assertEquals(3, service.runDueSips());
        assertEquals(15000.0, registration.getInvestedValue());
        assertEquals(300.0, registration.getUnits(), "5000 / 50 per instalment");
        assertEquals(15000.0, registration.getCurrentValue());
        assertEquals(LocalDate.of(2026, 11, 15), registration.getLastExecutedOn());

        assertEquals(0, service.runDueSips(), "the execution is idempotent");
        assertEquals(created.getId(), registration.getId());
    }

    @Test
    void aPausedSipIsNotExecuted() {
        createMonthly(5000, LocalDate.of(2026, 9, 15));
        service.changeStatus(rows.get(0).getId(), MfSipConstants.STATUS_PAUSED);

        assertEquals(0, service.runDueSips());
        assertEquals(0.0, rows.get(0).getInvestedValue());
    }

    @Test
    void aSipStopsAtItsEndDate() {
        service.create(request("HDFC Equity", MfSipConstants.TYPE_SIP, 1000.0,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 20)));

        assertEquals(2, service.runDueSips(), "Sep 15 and Oct 15 only");
        assertEquals(2000.0, rows.get(0).getInvestedValue());
    }

    @Test
    void aOneTimePurchaseIsInvestedImmediately() {
        MfSipRequest request = request("HDFC Equity", MfSipConstants.TYPE_ONE_TIME, 10000.0,
                LocalDate.of(2026, 11, 1), null);
        request.setNav(100.0);

        MfSipView view = service.create(request);

        assertEquals(MfSipConstants.TYPE_ONE_TIME, view.getRegistrationType());
        assertEquals(10000.0, view.getInvestedValue());
        assertEquals(100.0, view.getUnits());
        assertEquals(10000.0, view.getCurrentValue());
        assertEquals(0, service.runDueSips(), "a one time purchase has no schedule");
    }

    // ------------------------------------------------------------ #12 status

    @Test
    void statusChangesAreValidated() {
        MfSipView created = createMonthly(5000, LocalDate.of(2026, 9, 15));

        assertEquals(MfSipConstants.STATUS_PAUSED,
                service.changeStatus(created.getId(), "paused").getStatus());
        assertEquals(MfSipConstants.STATUS_ACTIVE,
                service.changeStatus(created.getId(), "ACTIVE").getStatus());

        ResponseStatusException unknown = assertThrows(ResponseStatusException.class,
                () -> service.changeStatus(created.getId(), "SLEEPING"));
        assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatusCode());
    }

    // ------------------------------------------------------------- #10 redeem

    @Test
    void redeemPartialThenFull() {
        createMonthly(5000, LocalDate.of(2026, 9, 15));
        MfSipRegistration registration = rows.get(0);
        registration.setNav(50);
        service.runDueSips();                    // 300 units, 15000 invested

        MfSipRedeemRequest partial = new MfSipRedeemRequest();
        partial.setMode(MfSipConstants.REDEEM_UNITS);
        partial.setValue(100);
        MfSipRedeemResult first = service.redeem(registration.getId(), partial);

        assertEquals(100.0, first.getUnitsSold());
        assertEquals(5000.0, first.getProceeds());
        assertEquals(5000.0, first.getCostBasis());
        assertEquals(200.0, registration.getUnits());
        assertEquals(10000.0, registration.getInvestedValue());

        MfSipRedeemRequest full = new MfSipRedeemRequest();
        full.setMode(MfSipConstants.REDEEM_FULL);
        MfSipRedeemResult second = service.redeem(registration.getId(), full);

        assertEquals(200.0, second.getUnitsSold());
        assertEquals(0.0, registration.getUnits());
        assertEquals(MfSipConstants.STATUS_CLOSED, registration.getStatus(),
                "a fully redeemed registration is CLOSED");
    }

    // ------------------------------------------------------------ #6 ownership

    @Test
    void anotherClientsRegistrationIsNotFound() {
        MfSipRegistration foreign = new MfSipRegistration();
        foreign.setClient(stranger);
        foreign.setSchemeName("SBI Bluechip");
        foreign.setRegistrationType(MfSipConstants.TYPE_SIP);
        foreign.setSipAmount(1000);
        foreign.setStatus(MfSipConstants.STATUS_ACTIVE);
        foreign.setSipStartDate(LocalDate.of(2026, 1, 1));
        repository.save(foreign);

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.get(foreign.getId()));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void listSummarisesTheClientsHoldings() {
        MfSipView created = createMonthly(5000, LocalDate.of(2026, 9, 15));
        rows.get(0).setNav(50);
        service.runDueSips();

        MfSipListResponse list = service.list();

        assertEquals(1, list.getRows().size());
        assertEquals(created.getId(), list.getRows().get(0).getId());
        assertEquals(15000.0, list.getSummary().get("invested"));
        assertEquals(300.0, list.getSummary().get("units"));
        assertEquals(1, list.getSummary().get("active"));
        assertEquals(0.0, list.getSummary().get("returns"));
    }

    @Test
    void aSipWithoutNavStillRecordsTheInvestedAmount() {
        createMonthly(2000, LocalDate.of(2026, 11, 15));

        assertEquals(1, service.runDueSips());
        assertEquals(2000.0, rows.get(0).getInvestedValue());
        assertEquals(0.0, rows.get(0).getUnits());
        assertEquals(2000.0, rows.get(0).getCurrentValue(), "no NAV yet - value = cost");
        assertNull(rows.get(0).getSipEndDate());
    }
}
