package com.artech.portfolio.bondsip.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.util.BondConstants;
import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.entity.BondSip;
import com.artech.portfolio.bondsip.repository.BondSipRepository;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.common.controller.AppParamController;
import com.artech.portfolio.common.entity.AppParamConfig;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BONDBP_V9 Bond SIP process (correction of BONDBP_V7/V8):
 *   #2/#3 create validations, Investment Date stamped server-side (renamed
 *         from Create Date), Status always CREATED,
 *   #4    a due instalment becomes a BOND row directly - status "In Progress",
 *         Investment Type "SIP Investment", Issuer / Investment Date /
 *         Interest Payment Start Date (SIP Date, weekend rolled) /
 *         Investment Amount Per Unit (SIP Amount) / Coupon Frequency (Frequency)
 *         mapped, everything else blank, generation idempotent and only for
 *         INPROGRESS SIPs,
 *   #5    syncStatus() powers the "Bond SIP Transaction is OK" banner,
 *   V8 #5 cancel rules unchanged.
 */
class BondSipServiceImplTest {

    // October 2026 reference dates: Sat 17, Sun 18, Mon 19, Tue 20.
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate FUTURE = LocalDate.of(2026, 11, 15);

    private BondSipRepository bondSipRepository;
    private BondRepository bondRepository;
    private AppParamController appParamController;
    private CurrentUserService currentUserService;

    private BondSipServiceImpl service;

    private Client owner;
    private Client stranger;
    private final List<BondSip> sips = new ArrayList<>();
    private final List<Bond> bonds = new ArrayList<>();
    private long nextSipId = 1;
    private long nextBondId = 1;

    @BeforeEach
    void setUp() {
        bondSipRepository = mock(BondSipRepository.class);
        bondRepository = mock(BondRepository.class);
        appParamController = mock(AppParamController.class);
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
        when(appParamController.getConfig()).thenReturn(configRows());

        // In-memory stores behind the repository mocks so idempotency is real.
        when(bondSipRepository.save(any(BondSip.class))).thenAnswer(inv -> {
            BondSip sip = inv.getArgument(0);
            if (sip.getId() == null) {
                sip.setId(nextSipId++);
            }
            sips.removeIf(s -> Objects.equals(s.getId(), sip.getId()));
            sips.add(sip);
            return sip;
        });
        when(bondSipRepository.findById(any())).thenAnswer(inv ->
                sips.stream().filter(s -> s.getId().equals(inv.getArgument(0))).findFirst());
        when(bondSipRepository.findByClientIdOrderByIdAsc(any())).thenAnswer(inv ->
                sips.stream()
                        .filter(s -> s.getClient() != null && s.getClient().getId().equals(inv.getArgument(0)))
                        .toList());
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                anyList(), any(LocalDate.class))).thenAnswer(inv -> {
                    List<String> statuses = inv.getArgument(0);
                    LocalDate until = inv.getArgument(1);
                    return sips.stream()
                            .filter(s -> statuses.contains(s.getStatus()))
                            .filter(s -> s.getSipDate() != null && !s.getSipDate().isAfter(until))
                            .sorted(java.util.Comparator.comparing(BondSip::getSipDate))
                            .toList();
                });

        when(bondRepository.save(any(Bond.class))).thenAnswer(inv -> {
            Bond bond = inv.getArgument(0);
            if (bond.getId() == null) {
                bond.setId(nextBondId++);
            }
            bonds.removeIf(b -> Objects.equals(b.getId(), bond.getId()));
            bonds.add(bond);
            return bond;
        });
        when(bondRepository.findByBondSipId(any())).thenAnswer(inv ->
                bonds.stream()
                        .filter(b -> Objects.equals(b.getBondSipId(), inv.getArgument(0)))
                        .toList());

        Clock clock = Clock.fixed(TUESDAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new BondSipServiceImpl(bondSipRepository, bondRepository,
                appParamController, currentUserService, clock);
    }

    // ------------------------------------------------------------- fixtures

    private List<AppParamConfig> configRows() {
        return List.of(
                configRow(1L, BondConstants.ISSUER, "HDFC Bank"),
                configRow(2L, BondConstants.ISSUER, "SBI"),
                configRow(10L, BondConstants.COUPON_FREQUENCY, "Weekly"),
                configRow(11L, BondConstants.COUPON_FREQUENCY, "Monthly"),
                configRow(12L, BondConstants.COUPON_FREQUENCY, "Quarterly"),
                configRow(13L, BondConstants.COUPON_FREQUENCY, "Half Yearly"),
                configRow(14L, BondConstants.COUPON_FREQUENCY, "Yearly"));
    }

    private AppParamConfig configRow(Long id, String name, String value) {
        AppParamConfig row = new AppParamConfig();
        row.setId(id);
        row.setInvestmentType(BondConstants.BOND);
        row.setParamName(name);
        row.setParamValue(value);
        return row;
    }

    private CreateBondSipRequest request(String issuer, LocalDate sipDate, Double amount, String frequency) {
        CreateBondSipRequest request = new CreateBondSipRequest();
        request.setIssuer(issuer);
        request.setSipDate(sipDate);
        request.setSipAmount(amount);
        request.setFrequency(frequency);
        return request;
    }

    private ResponseStatusException expectRejected(CreateBondSipRequest request) {
        return assertThrows(ResponseStatusException.class, () -> service.create(request));
    }

    // ------------------------------------------------------------ #2 #3 create

    @Test
    void createStampsInvestmentDateAndCreatedStatus() {
        BondSipView view = service.create(request("HDFC Bank", FUTURE, 5000.0, "MONTHLY"));

        assertEquals(TUESDAY, view.getInvestmentDate(), "#3 Investment Date = today (server side)");
        assertEquals("CREATED", view.getStatus(), "#3 status always CREATED on create");
        assertEquals("HDFC Bank", view.getIssuer());
        assertEquals(5000.0, view.getSipAmount());
        assertEquals("MONTHLY", view.getFrequency());
        assertTrue(bonds.isEmpty(), "a future SIP generates nothing yet");
    }

    @Test
    void createRejectsBlankIssuer() {
        ResponseStatusException error = expectRejected(request("   ", FUTURE, 5000.0, "MONTHLY"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("Issuer is required"));
    }

    @Test
    void createRejectsUnconfiguredIssuer() {
        ResponseStatusException error = expectRejected(request("Unknown Co", FUTURE, 5000.0, "MONTHLY"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("not configured"));
    }

    @Test
    void createRejectsMissingSipDate() {
        ResponseStatusException error = expectRejected(request("HDFC Bank", null, 5000.0, "MONTHLY"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("SIP Date is required"));
    }

    @Test
    void createRejectsZeroAmount() {
        ResponseStatusException error = expectRejected(request("HDFC Bank", FUTURE, 0.0, "MONTHLY"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("greater than 0"));
    }

    @Test
    void createRejectsUnknownFrequency() {
        ResponseStatusException error = expectRejected(request("HDFC Bank", FUTURE, 5000.0, "HOURLY"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("Frequency"));
    }

    // --------------------------------------------------------------- #4 bond rows

    @Test
    void pastDatedSipBackfillsBondRowsWithV9Mapping() {
        // Saturday SIP date -> weekend rolled to Monday (#4), one instalment due by Tuesday.
        BondSipView view = service.create(request("HDFC Bank", SATURDAY, 5000.0, "MONTHLY"));

        assertEquals("INPROGRESS", view.getStatus(), "backfilled SIP turns INPROGRESS");
        assertEquals(1, bonds.size());

        Bond row = bonds.get(0);
        assertEquals("In Progress", row.getStatus(), "#4.b status");
        assertEquals("SIP Investment", row.getInvestmentType(), "#4.b / #2 investment type");
        assertEquals(view.getId(), row.getBondSipId(), "linked to its Bond SIP");
        assertEquals("1", row.getIssuer(), "#4.b issuer resolves to the shared config id - same name on all screens");
        assertEquals(TUESDAY, row.getInvestmentDate(), "#3/#4.b Investment Date carried over");
        assertEquals(MONDAY, row.getInterestPaymentStartDate(), "#4.c SIP Date, weekend rolled");
        assertEquals(5000.0, row.getInvestmentAmountPerUnit(), "#4.d SIP Amount default");
        assertEquals("11", row.getCouponFrequency(), "#4.e Frequency maps to coupon-frequency config");
        assertNull(row.getBondName(), "#4.f unmapped columns stay blank");
        assertEquals(0, row.getUnits(), "#4.f units blank until the user completes the entry");
        assertEquals(owner.getId(), row.getClient().getId(), "ownership from the session");
    }

    @Test
    void runDueProcessesOnlyInProgressSips() {
        BondSip created = new BondSip();
        created.setClient(owner);
        created.setIssuer("HDFC Bank");
        created.setInvestmentDate(TUESDAY);
        created.setSipDate(MONDAY);
        created.setSipAmount(1000.0);
        created.setFrequency("MONTHLY");
        created.setStatus("CREATED");
        bondSipRepository.save(created);

        assertEquals(0, service.runDueSips(), "#V8 #2: CREATED SIPs are not processed");
        assertTrue(bonds.isEmpty());

        created.setStatus("INPROGRESS");
        bondSipRepository.save(created);
        assertEquals(1, service.runDueSips());
        assertEquals(1, bonds.size());
    }

    @Test
    void runDueIsIdempotent() {
        service.create(request("HDFC Bank", SATURDAY, 5000.0, "MONTHLY"));
        assertEquals(1, bonds.size());

        assertEquals(0, service.runDueSips(), "an instalment with a bond row is never re-created");
        assertEquals(0, service.runDueSips());
        assertEquals(1, bonds.size());
    }

    // ------------------------------------------------------------ #5 sync banner

    @Test
    void syncStatusIsOkWhenEveryDueInstalmentHasItsBondRow() {
        service.create(request("HDFC Bank", SATURDAY, 5000.0, "MONTHLY"));

        var status = service.syncStatus();

        assertEquals(Boolean.TRUE, status.get("ok"));
        assertTrue(String.valueOf(status.get("message")).contains("Bond SIP Transaction is OK"),
                String.valueOf(status.get("message")));
        assertEquals(1, status.get("due"));
        assertEquals(0, status.get("pending"));
    }

    @Test
    void syncStatusReportsMissingInstalments() {
        BondSip sip = new BondSip();
        sip.setClient(owner);
        sip.setIssuer("SBI");
        sip.setInvestmentDate(TUESDAY);
        sip.setSipDate(MONDAY);           // due yesterday, but no bond row exists
        sip.setSipAmount(2500.0);
        sip.setFrequency("MONTHLY");
        sip.setStatus("INPROGRESS");
        bondSipRepository.save(sip);

        var status = service.syncStatus();

        assertEquals(Boolean.FALSE, status.get("ok"));
        assertEquals(1, status.get("pending"));
        assertTrue(String.valueOf(status.get("message")).contains("missing"));
    }

    // ------------------------------------------------------------- V8 #5 cancel

    @Test
    void cancelTurnsTheSipCancelled() {
        BondSipView created = service.create(request("HDFC Bank", FUTURE, 5000.0, "MONTHLY"));
        BondSipView cancelled = service.cancelSip(created.getId());
        assertEquals("CANCELLED", cancelled.getStatus());
    }

    @Test
    void cancelRejectsOtherPeersSipAndDoubleCancel() {
        BondSipView created = service.create(request("HDFC Bank", FUTURE, 5000.0, "MONTHLY"));

        BondSip foreign = new BondSip();
        foreign.setClient(stranger);
        foreign.setIssuer("SBI");
        foreign.setSipDate(FUTURE);
        foreign.setSipAmount(100.0);
        foreign.setFrequency("MONTHLY");
        foreign.setStatus("CREATED");
        bondSipRepository.save(foreign);

        ResponseStatusException notMine = assertThrows(ResponseStatusException.class,
                () -> service.cancelSip(foreign.getId()));
        assertEquals(HttpStatus.NOT_FOUND, notMine.getStatusCode());

        service.cancelSip(created.getId());
        ResponseStatusException again = assertThrows(ResponseStatusException.class,
                () -> service.cancelSip(created.getId()));
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
        assertTrue(again.getReason().contains("already cancelled"));
    }

    // ------------------------------------------------------------------ helpers

    @Test
    void helperFrequencyNormalisation() {
        assertEquals("MONTHLY", BondSipServiceImpl.normalizeFrequency("monthly"));
        assertEquals("HALF-YEARLY", BondSipServiceImpl.normalizeFrequency("half yearly"));
        assertEquals("QUARTERLY", BondSipServiceImpl.normalizeFrequency(" QUARTERLY "));
        assertNull(BondSipServiceImpl.normalizeFrequency("hourly"));
        assertNull(BondSipServiceImpl.normalizeFrequency(null));
    }

    @Test
    void helperWeekendRollAndSchedule() {
        assertEquals(MONDAY, BondSipServiceImpl.effectiveSipDate(SATURDAY));
        assertEquals(MONDAY, BondSipServiceImpl.effectiveSipDate(SUNDAY));
        assertEquals(TUESDAY, BondSipServiceImpl.effectiveSipDate(TUESDAY));

        assertEquals(LocalDate.of(2026, 2, 28),
                BondSipServiceImpl.nextInstalmentDate(LocalDate.of(2026, 1, 31), "MONTHLY"),
                "month-end instalments clamp (Jan 31 -> Feb 28)");
        assertEquals(LocalDate.of(2026, 11, 17),
                BondSipServiceImpl.nextInstalmentDate(SATURDAY, "MONTHLY"));
        assertEquals(LocalDate.of(2026, 10, 24),
                BondSipServiceImpl.nextInstalmentDate(SATURDAY, "WEEKLY"));
        assertEquals(LocalDate.of(2027, 10, 17),
                BondSipServiceImpl.nextInstalmentDate(SATURDAY, "YEARLY"));
    }

    @Test
    void helperDueEffectiveDatesWalkTheFrequencyUpToToday() {
        BondSip sip = new BondSip();
        sip.setSipDate(LocalDate.of(2026, 10, 5));
        sip.setFrequency("WEEKLY");

        List<LocalDate> due = BondSipServiceImpl.dueEffectiveDates(sip, TUESDAY);

        assertEquals(List.of(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19)), due);
        assertTrue(BondSipServiceImpl.dueEffectiveDates(sip, LocalDate.of(2026, 10, 4)).isEmpty());
    }
}
