package com.artech.portfolio.bondsip.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bondsip.dto.BondSipBondOption;
import com.artech.portfolio.bondsip.dto.BondSipTransactionView;
import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.dto.UpdateBondSipTransactionRequest;
import com.artech.portfolio.bondsip.entity.BondSip;
import com.artech.portfolio.bondsip.entity.BondSipTransaction;
import com.artech.portfolio.bondsip.repository.BondSipRepository;
import com.artech.portfolio.bondsip.repository.BondSipTransactionRepository;
import com.artech.portfolio.bondsip.util.BondSipConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.common.controller.AppParamController;
import com.artech.portfolio.common.entity.AppParamConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BONDBP_V7 Bond SIP rules:
 *   #2 mandatory Issuer / SIP Date / SIP Amount, Create Date auto from the clock,
 *      Issuer must be one of the configured bond issuers (app_param_config,
 *      param name: issuer, from the cached App Config object),
 *   #3 status always CREATED on create (never client-supplied),
 *   #4 auto-execution per the Frequency schedule (weekly..yearly): each due
 *      instalment creates one INPROGRESS transaction, a past-dated SIP is
 *      backfilled with every instalment due, weekend dates roll to the next
 *      weekday, generation is idempotent,
 *   #5/#7 only Actual SIP Amount + Bond Name may change, association -> EXECUTED,
 *   #6 bond options = own + family bonds labelled "Bond Name|PAN", first
 *      occurrence of a duplicated (owner, bond name) pair wins.
 */
class BondSipServiceImplTest {

    // October 2026 reference dates: Sat 17, Sun 18, Mon 19, Tue 20, Wed 21.
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private BondSipRepository bondSipRepository;
    private BondSipTransactionRepository transactionRepository;
    private BondRepository bondRepository;
    private AppParamController appParamController;
    private CurrentUserService currentUserService;
    private Clock clock;

    private BondSipServiceImpl service;

    private Client owner;
    private Client member;

    @BeforeEach
    void setUp() {
        bondSipRepository = mock(BondSipRepository.class);
        transactionRepository = mock(BondSipTransactionRepository.class);
        bondRepository = mock(BondRepository.class);
        appParamController = mock(AppParamController.class);
        currentUserService = mock(CurrentUserService.class);
        clock = clockOn(TUESDAY);
        // The configurable Issuer master: bond+issuer rows the create flow may
        // use, plus decoys under another investment type / param name that must
        // NOT be offered (same filter as /api/bonds/issuer).
        when(appParamController.getConfig()).thenReturn(List.of(
                configRow("bond", "issuer", "HDFC Bank"),
                configRow("bond", "issuer", "SBI"),
                configRow("bond", "issuer", "Bajaj"),
                configRow("fd", "issuer", "FD House"),          // decoy: other type
                configRow("bond", "credit-rating", "AAA")));   // decoy: other param
        service = new BondSipServiceImpl(bondSipRepository, transactionRepository,
                bondRepository, appParamController, currentUserService, clock);

        owner = new Client();
        owner.setId(1L);
        owner.setName("Ramesh Kumar");
        owner.setPan("ABCDE1234F");

        member = new Client();
        member.setId(2L);
        member.setName("Sunita Kumar");
        member.setPan("QRSXY5678Z");

        when(currentUserService.requireClient()).thenReturn(owner);
    }

    private static Clock clockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
    }

    private static AppParamConfig configRow(String type, String name, String value) {
        AppParamConfig row = new AppParamConfig();
        row.setInvestmentType(type);
        row.setParamName(name);
        row.setParamValue(value);
        return row;
    }

    private static CreateBondSipRequest request(String issuer, LocalDate sipDate, Double amount) {
        return request(issuer, sipDate, amount, BondSipConstants.FREQUENCY_MONTHLY);
    }

    private static CreateBondSipRequest request(String issuer, LocalDate sipDate, Double amount,
                                                String frequency) {
        CreateBondSipRequest request = new CreateBondSipRequest();
        request.setIssuer(issuer);
        request.setSipDate(sipDate);
        request.setSipAmount(amount);
        request.setFrequency(frequency);
        return request;
    }

    private BondSip sip(LocalDate sipDate, String status) {
        BondSip sip = new BondSip();
        sip.setId(10L);
        sip.setClient(owner);
        sip.setIssuer("HDFC Bank");
        sip.setCreateDate(TUESDAY.minusDays(7));
        sip.setSipDate(sipDate);
        sip.setSipAmount(5000);
        sip.setFrequency(BondSipConstants.FREQUENCY_MONTHLY);
        sip.setStatus(status);
        return sip;
    }

    /** Active = CREATED + INPROGRESS, the exact list runDueSips queries. */
    private static List<String> activeStatuses() {
        return List.of(BondSipConstants.STATUS_CREATED, BondSipConstants.STATUS_INPROGRESS);
    }

    private Bond bond(Long id, String name, Client client) {
        Bond bond = new Bond();
        bond.setId(id);
        bond.setBondName(name);
        bond.setClient(client);
        return bond;
    }

    // ------------------------------------------------------------ #2 #3 create

    @Test
    void createStampsTodayAsCreateDateAndStatusCreatedForTheSessionClient() {
        when(bondSipRepository.save(any(BondSip.class))).thenAnswer(inv -> {
            BondSip s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        BondSipView view = service.create(request("HDFC Bank", LocalDate.of(2026, 11, 5), 5000.0));

        ArgumentCaptor<BondSip> captor = ArgumentCaptor.forClass(BondSip.class);
        verify(bondSipRepository).save(captor.capture());
        BondSip saved = captor.getValue();
        assertEquals(1L, saved.getClient().getId(), "ownership from session, never body");
        assertEquals(LocalDate.of(2026, 10, 20), saved.getCreateDate(), "#2 auto = current date");
        assertEquals(BondSipConstants.STATUS_CREATED, saved.getStatus(),
                "#3 CREATED - nothing due yet, the SIP starts next month");
        assertEquals(BondSipConstants.FREQUENCY_MONTHLY, saved.getFrequency());
        assertEquals(BondSipConstants.FREQUENCY_MONTHLY, view.getFrequency());
        assertNull(view.getActualSipAmount());
        assertNull(view.getBondName());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingFrequency() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("HDFC Bank", TUESDAY, 5000.0, null)));
        assertEquals("Frequency is required", ex.getReason());
        verify(bondSipRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownFrequency() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("HDFC Bank", TUESDAY, 5000.0, "BIWEEKLY")));
        assertEquals("Frequency must be one of WEEKLY, MONTHLY, QUARTERLY, HALF-YEARLY, YEARLY",
                ex.getReason());
        verify(bondSipRepository, never()).save(any());
    }

    @Test
    void createNormalizesFrequencyCaseAndSeparators() {
        when(bondSipRepository.save(any(BondSip.class))).thenAnswer(inv -> {
            BondSip s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        service.create(request("HDFC Bank", LocalDate.of(2026, 11, 5), 5000.0, " half yearly "));

        ArgumentCaptor<BondSip> captor = ArgumentCaptor.forClass(BondSip.class);
        verify(bondSipRepository).save(captor.capture());
        assertEquals(BondSipConstants.FREQUENCY_HALF_YEARLY, captor.getValue().getFrequency());
    }

    // ------------------------------------------------- #4 frequency backfill

    @Test
    void createBackfillsEveryPastMonthlyInstalmentAsInProgress() {
        when(bondSipRepository.save(any(BondSip.class))).thenAnswer(inv -> {
            BondSip s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(transactionRepository.save(any(BondSipTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Started 5 months ago, monthly -> 6 instalments due (start month .. today).
        BondSipView view = service.create(
                request("HDFC Bank", TUESDAY.minusMonths(5), 5000.0, BondSipConstants.FREQUENCY_MONTHLY));

        ArgumentCaptor<BondSipTransaction> captor = ArgumentCaptor.forClass(BondSipTransaction.class);
        verify(transactionRepository, times(6)).save(captor.capture());
        List<LocalDate> dates = captor.getAllValues().stream()
                .map(BondSipTransaction::getSipDate).sorted().toList();
        // 2026-06-20 is a Saturday -> rolled to 2026-06-22; 2026-09-20 is a Sunday -> rolled to 2026-09-21.
        assertEquals(List.of(
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 20)),
                dates, "one transaction per monthly instalment from the SIP date to today (weekend-rolled)");
        captor.getAllValues().forEach(tx ->
                assertEquals(BondSipConstants.STATUS_INPROGRESS, tx.getStatus(),
                        "#4 backfilled rows wait INPROGRESS on the transaction screen"));
        assertEquals(BondSipConstants.STATUS_INPROGRESS, view.getStatus(),
                "parent SIP turns INPROGRESS once instalments exist");
    }

    @Test
    void createBackfillsWeeklyInstalments() {
        when(bondSipRepository.save(any(BondSip.class))).thenAnswer(inv -> {
            BondSip s = inv.getArgument(0);
            s.setId(11L);
            return s;
        });
        when(transactionRepository.save(any(BondSipTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Monday 21 Sep, weekly -> 21, 28 Sep, 5, 12, 19 Oct = 5 instalments.
        service.create(request("SBI", LocalDate.of(2026, 9, 21), 1000.0,
                BondSipConstants.FREQUENCY_WEEKLY));

        verify(transactionRepository, times(5)).save(any(BondSipTransaction.class));
    }

    @Test
    void createRollsEachWeekendInstalmentToMonday() {
        when(bondSipRepository.save(any(BondSip.class))).thenAnswer(inv -> {
            BondSip s = inv.getArgument(0);
            s.setId(12L);
            return s;
        });
        when(transactionRepository.save(any(BondSipTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Saturday 15 Aug, monthly: 15 Aug (Sat -> Mon 17), 15 Sep (Tue), 15 Oct (Thu).
        service.create(request("Bajaj", LocalDate.of(2026, 8, 15), 2000.0,
                BondSipConstants.FREQUENCY_MONTHLY));

        ArgumentCaptor<BondSipTransaction> captor = ArgumentCaptor.forClass(BondSipTransaction.class);
        verify(transactionRepository, times(3)).save(captor.capture());
        List<LocalDate> dates = captor.getAllValues().stream()
                .map(BondSipTransaction::getSipDate).sorted().toList();
        assertEquals(List.of(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 15)), dates, "#4 weekend instalments roll to Monday");
    }

    @Test
    void createRejectsBlankIssuer() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("  ", LocalDate.of(2026, 11, 5), 5000.0)));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("Issuer is required", ex.getReason());
        verify(bondSipRepository, never()).save(any());
    }

    @Test
    void createRejectsAnIssuerThatIsNotInTheConfiguredList() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("ICICI", LocalDate.of(2026, 11, 5), 5000.0)));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("Issuer is not configured - pick one from Application Parameter "
                + "Configuration (param name: issuer)", ex.getReason());
        verify(bondSipRepository, never()).save(any());
    }

    @Test
    void createRejectsAnIssuerConfiguredUnderAnotherInvestmentTypeOrParamName() {
        // "FD House" exists in app_param_config but with investment type fd -
        // only bond+issuer rows feed the Bond SIP drop-down (same as /api/bonds/issuer).
        assertEquals("Issuer is not configured - pick one from Application Parameter "
                        + "Configuration (param name: issuer)",
                assertThrows(ResponseStatusException.class,
                        () -> service.create(request("FD House", LocalDate.of(2026, 11, 5), 5000.0)))
                        .getReason());
    }

    @Test
    void createRejectsWhenNoIssuersAreConfiguredAtAll() {
        when(appParamController.getConfig()).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("HDFC Bank", LocalDate.of(2026, 11, 5), 5000.0)));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("Issuer is not configured - pick one from Application Parameter "
                + "Configuration (param name: issuer)", ex.getReason());
        verify(bondSipRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingSipDate() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("HDFC Bank", null, 5000.0)));
        assertEquals("SIP Date is required", ex.getReason());
    }

    @Test
    void createRejectsZeroOrNegativeSipAmount() {
        assertEquals("SIP Amount must be greater than 0",
                assertThrows(ResponseStatusException.class,
                        () -> service.create(request("HDFC Bank", TUESDAY, 0.0))).getReason());
        assertEquals("SIP Amount must be greater than 0",
                assertThrows(ResponseStatusException.class,
                        () -> service.create(request("HDFC Bank", TUESDAY, -10.0))).getReason());
        assertEquals("SIP Amount must be greater than 0",
                assertThrows(ResponseStatusException.class,
                        () -> service.create(request("HDFC Bank", TUESDAY, null))).getReason());
    }

    // ------------------------------------------------------------------ #4 run

    @Test
    void runDueExecutesAWeekdaySipAndCreatesTheInProgressTransaction() {
        // BONDBP_V8 #2: only INPROGRESS SIPs generate transactions
        BondSip due = sip(TUESDAY, BondSipConstants.STATUS_INPROGRESS);
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                eq(List.of(BondSipConstants.STATUS_INPROGRESS)), eq(TUESDAY))).thenReturn(List.of(due));
        when(transactionRepository.save(any(BondSipTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int executed = service.runDueSips();

        assertEquals(1, executed);
        assertEquals(BondSipConstants.STATUS_INPROGRESS, due.getStatus());
        ArgumentCaptor<BondSipTransaction> captor = ArgumentCaptor.forClass(BondSipTransaction.class);
        verify(transactionRepository).save(captor.capture());
        BondSipTransaction tx = captor.getValue();
        assertEquals(BondSipConstants.STATUS_INPROGRESS, tx.getStatus(), "#4 created INPROGRESS");
        assertEquals("HDFC Bank", tx.getIssuer());
        assertEquals(TUESDAY.minusDays(7), tx.getCreateDate());
        assertEquals(TUESDAY, tx.getSipDate());
        assertEquals(5000, tx.getSipAmount());
        assertSame(due, tx.getBondSip());
    }

    @Test
    void runDueLeavesFutureSipsUntouched() {
        BondSip future = sip(LocalDate.of(2026, 10, 27), BondSipConstants.STATUS_CREATED);
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                eq(activeStatuses()), eq(TUESDAY))).thenReturn(List.of());

        assertEquals(0, service.runDueSips());
        assertEquals(BondSipConstants.STATUS_CREATED, future.getStatus());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void runDueSkipsAWeekendSipUntilMonday() {
        // BONDBP_V8 #2: only INPROGRESS SIPs generate transactions
        BondSip weekendSip = sip(SATURDAY, BondSipConstants.STATUS_INPROGRESS);
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                eq(List.of(BondSipConstants.STATUS_INPROGRESS)), eq(SATURDAY))).thenReturn(List.of(weekendSip));
        assertEquals(0, service.runDueSips(), "#4 rolls Sat -> next weekday");

        // ...nor on Sunday...
        service = new BondSipServiceImpl(bondSipRepository, transactionRepository,
                bondRepository, appParamController, currentUserService, clockOn(SUNDAY));
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                eq(List.of(BondSipConstants.STATUS_INPROGRESS)), eq(SUNDAY))).thenReturn(List.of(weekendSip));
        assertEquals(0, service.runDueSips());

        // ...but on Monday 19 Oct.
        service = new BondSipServiceImpl(bondSipRepository, transactionRepository,
                bondRepository, appParamController, currentUserService, clockOn(MONDAY));
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                eq(List.of(BondSipConstants.STATUS_INPROGRESS)), eq(MONDAY))).thenReturn(List.of(weekendSip));
        when(transactionRepository.save(any(BondSipTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        assertEquals(1, service.runDueSips());
    }

    @Test
    void runDueRollsSundayToMonday() {
        BondSip sundaySip = sip(SUNDAY, BondSipConstants.STATUS_INPROGRESS);
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                eq(List.of(BondSipConstants.STATUS_INPROGRESS)), eq(MONDAY))).thenReturn(List.of(sundaySip));
        when(transactionRepository.save(any(BondSipTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new BondSipServiceImpl(bondSipRepository, transactionRepository,
                bondRepository, appParamController, currentUserService, clockOn(MONDAY));
        assertEquals(1, service.runDueSips());

        ArgumentCaptor<BondSipTransaction> captor = ArgumentCaptor.forClass(BondSipTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(MONDAY, captor.getValue().getSipDate(), "#4 transaction carries the rolled date");
    }

    @Test
    void runDueIsIdempotent() {
        BondSip due = sip(TUESDAY, BondSipConstants.STATUS_CREATED);
        List<BondSipTransaction> stored = new ArrayList<>();
        // Mirror the real query: both active statuses come back while the SIP exists.
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                any(), eq(TUESDAY))).thenReturn(List.of(due));
        when(transactionRepository.findByBondSipId(10L)).thenAnswer(inv -> List.copyOf(stored));
        when(transactionRepository.save(any(BondSipTransaction.class))).thenAnswer(inv -> {
            BondSipTransaction tx = inv.getArgument(0);
            stored.add(tx);
            return tx;
        });

        assertEquals(1, service.runDueSips());
        assertEquals(0, service.runDueSips(), "instalment already generated - no duplicate");
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void runDueContinuesTheScheduleForAlreadyInProgressSips() {
        // Parent INPROGRESS with only its first instalment (Thu 20 Aug) stored:
        // Sun 20 Sep rolls to Mon 21 Sep, Tue 20 Oct is due - 2 new rows.
        BondSip active = sip(TUESDAY.minusMonths(2), BondSipConstants.STATUS_INPROGRESS);
        List<BondSipTransaction> stored = new ArrayList<>();
        BondSipTransaction first = new BondSipTransaction();
        first.setBondSip(active);
        first.setSipDate(TUESDAY.minusMonths(2));
        stored.add(first);
        when(bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                any(), eq(TUESDAY))).thenReturn(List.of(active));
        when(transactionRepository.findByBondSipId(10L)).thenAnswer(inv -> List.copyOf(stored));
        when(transactionRepository.save(any(BondSipTransaction.class))).thenAnswer(inv -> {
            BondSipTransaction tx = inv.getArgument(0);
            stored.add(tx);
            return tx;
        });

        assertEquals(2, service.runDueSips(), "recurring: Sep + Oct instalments generated");
        assertEquals(0, service.runDueSips(), "idempotent on re-run");
        assertEquals(BondSipConstants.STATUS_INPROGRESS, active.getStatus());
    }

    @Test
    void nextInstalmentDateAdvancesByFrequency() {
        assertEquals(LocalDate.of(2026, 10, 27),
                BondSipServiceImpl.nextInstalmentDate(TUESDAY, BondSipConstants.FREQUENCY_WEEKLY));
        assertEquals(LocalDate.of(2026, 11, 20),
                BondSipServiceImpl.nextInstalmentDate(TUESDAY, BondSipConstants.FREQUENCY_MONTHLY));
        assertEquals(LocalDate.of(2027, 1, 20),
                BondSipServiceImpl.nextInstalmentDate(TUESDAY, BondSipConstants.FREQUENCY_QUARTERLY));
        assertEquals(LocalDate.of(2027, 4, 20),
                BondSipServiceImpl.nextInstalmentDate(TUESDAY, BondSipConstants.FREQUENCY_HALF_YEARLY));
        assertEquals(LocalDate.of(2027, 10, 20),
                BondSipServiceImpl.nextInstalmentDate(TUESDAY, BondSipConstants.FREQUENCY_YEARLY));
    }

    @Test
    void normalizeFrequencyCanonicalisesInputAndRejectsUnknownValues() {
        assertEquals(BondSipConstants.FREQUENCY_MONTHLY,
                BondSipServiceImpl.normalizeFrequency(" monthly "));
        assertEquals(BondSipConstants.FREQUENCY_HALF_YEARLY,
                BondSipServiceImpl.normalizeFrequency("half yearly"));
        assertEquals(BondSipConstants.FREQUENCY_HALF_YEARLY,
                BondSipServiceImpl.normalizeFrequency("Half_Yearly"));
        assertNull(BondSipServiceImpl.normalizeFrequency("BIWEEKLY"));
        assertNull(BondSipServiceImpl.normalizeFrequency("  "));
        assertNull(BondSipServiceImpl.normalizeFrequency(null));
    }

    @Test
    void effectiveSipDateRollsOnlyWeekendDates() {
        assertEquals(MONDAY, BondSipServiceImpl.effectiveSipDate(SATURDAY));
        assertEquals(MONDAY, BondSipServiceImpl.effectiveSipDate(SUNDAY));
        assertEquals(TUESDAY, BondSipServiceImpl.effectiveSipDate(TUESDAY));
        assertEquals(LocalDate.of(2026, 10, 21), BondSipServiceImpl.effectiveSipDate(LocalDate.of(2026, 10, 21)));
    }

    // ----------------------------------------------------------- #5 #7 update

    private BondSipTransaction inProgressTransaction() {
        BondSip parent = sip(TUESDAY, BondSipConstants.STATUS_INPROGRESS);
        BondSipTransaction tx = new BondSipTransaction();
        tx.setId(100L);
        tx.setBondSip(parent);
        tx.setIssuer(parent.getIssuer());
        tx.setCreateDate(parent.getCreateDate());
        tx.setSipDate(parent.getSipDate());
        tx.setSipAmount(parent.getSipAmount());
        tx.setStatus(BondSipConstants.STATUS_INPROGRESS);
        return tx;
    }

    private UpdateBondSipTransactionRequest update(Long bondId, Double actual) {
        UpdateBondSipTransactionRequest request = new UpdateBondSipTransactionRequest();
        request.setBondId(bondId);
        request.setActualSipAmount(actual);
        return request;
    }

    @Test
    void associatingABondExecutesTheTransactionAndMirrorsTheActualAmount() {
        BondSipTransaction tx = inProgressTransaction();
        Bond own = bond(50L, "Alpha NCD", owner);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(tx));
        when(bondRepository.findByClientIdIn(List.of(1L))).thenReturn(List.of(own));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bondSipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BondSipTransactionView view = service.updateTransaction(100L, update(50L, 4500.0));

        assertEquals(BondSipConstants.STATUS_EXECUTED, tx.getStatus(), "#7");
        assertSame(own, tx.getBond());
        assertEquals(4500.0, tx.getActualSipAmount());
        assertEquals(4500.0, tx.getBondSip().getActualSipAmount(), "mirrored to the SIP screen");
        assertEquals("Alpha NCD", view.getBondName());
        assertEquals(50L, view.getBondId());
    }

    @Test
    void updateRejectsMissingBond() {
        BondSipTransaction tx = inProgressTransaction();
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(tx));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateTransaction(100L, update(null, 100.0)));
        assertEquals("Bond Name is required to execute the transaction", ex.getReason());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void updateRejectsBondOutsideOwnAndFamilyPortfolio() {
        BondSipTransaction tx = inProgressTransaction();
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(tx));
        when(bondRepository.findByClientIdIn(List.of(1L))).thenReturn(List.of(bond(50L, "Alpha", owner)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateTransaction(100L, update(999L, 100.0)));
        assertEquals("Bond not found in your or your family member's portfolio", ex.getReason());
    }

    @Test
    void updateRejectsAlreadyExecutedTransaction() {
        BondSipTransaction tx = inProgressTransaction();
        tx.setStatus(BondSipConstants.STATUS_EXECUTED);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(tx));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateTransaction(100L, update(50L, 100.0)));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
    }

    @Test
    void updateRejectsTransactionsOfOtherClients() {
        BondSipTransaction tx = inProgressTransaction();
        tx.getBondSip().setClient(member); // belongs to Sunita
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(tx));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateTransaction(100L, update(50L, 100.0)));
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
    }

    @Test
    void updateRejectsNegativeActualAmount() {
        BondSipTransaction tx = inProgressTransaction();
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(tx));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateTransaction(100L, update(50L, -5.0)));
        assertEquals("Actual SIP Amount cannot be negative", ex.getReason());
    }

    // ------------------------------------------------------------- #6 options

    @Test
    void bondOptionsListOwnAndFamilyBondsAsNamePanAndKeepFirstOccurrence() {
        ClientFamilyMember link = new ClientFamilyMember();
        link.setMember(member);
        owner.addFamilyMember(link);

        Bond first = bond(50L, "Alpha NCD", owner);
        Bond duplicate = bond(55L, "Alpha NCD", owner);      // same owner + name
        Bond familyBond = bond(60L, "Alpha NCD", member);    // same name, other PAN
        when(bondRepository.findByClientIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(duplicate, familyBond, first)); // shuffled on purpose

        List<BondSipBondOption> options = service.bondOptions();

        assertEquals(2, options.size(), "#6 duplicate (owner, bond name) collapses to first");
        assertEquals(50L, options.get(0).getBondId(), "lowest id = first occurrence");
        assertEquals("Alpha NCD|ABCDE1234F", options.get(0).getLabel());
        assertEquals(60L, options.get(1).getBondId());
        assertEquals("Alpha NCD|QRSXY5678Z", options.get(1).getLabel());
    }

    @Test
    void bondOptionsIncludeFamilyBondsWithoutAnyFamily() {
        Bond own = bond(50L, "Gamma Bond", owner);
        when(bondRepository.findByClientIdIn(List.of(1L))).thenReturn(List.of(own));

        List<BondSipBondOption> options = service.bondOptions();

        assertEquals(1, options.size());
        assertEquals("Gamma Bond|ABCDE1234F", options.get(0).getLabel());
    }

    // ---------------------------------------------------------- list derivation

    @Test
    void listMineDerivesBondNameFromTheFirstTransactionOfTheSip() {
        BondSip sip = sip(TUESDAY, BondSipConstants.STATUS_EXECUTED);
        when(bondSipRepository.findByClientIdOrderByIdAsc(1L)).thenReturn(List.of(sip));
        BondSipTransaction tx = new BondSipTransaction();
        tx.setBondSip(sip);
        tx.setBond(bond(50L, "Alpha NCD", owner));
        when(transactionRepository.findByBondSipClientIdOrderByIdAsc(1L)).thenReturn(List.of(tx));

        List<BondSipView> list = service.listMine();

        assertEquals(1, list.size());
        assertEquals("Alpha NCD", list.get(0).getBondName());
        assertEquals(BondSipConstants.STATUS_EXECUTED, list.get(0).getStatus());
    }
}
