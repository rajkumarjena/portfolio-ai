package com.artech.portfolio.monthlyplanner.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bondsip.entity.BondSip;
import com.artech.portfolio.bondsip.repository.BondSipRepository;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.creditcard.service.CreditCardService;
import com.artech.portfolio.mfsip.entity.MfSipRegistration;
import com.artech.portfolio.mfsip.repository.MfSipRegistrationRepository;
import com.artech.portfolio.mfsip.util.MfSipConstants;
import com.artech.portfolio.monthlyplanner.dto.PlannerEntryRequest;
import com.artech.portfolio.monthlyplanner.dto.PlannerLineView;
import com.artech.portfolio.monthlyplanner.dto.PlannerMonthView;
import com.artech.portfolio.monthlyplanner.dto.SavePlannerRequest;
import com.artech.portfolio.monthlyplanner.entity.MonthlyPlanEntry;
import com.artech.portfolio.monthlyplanner.entity.MonthlyPlanHistory;
import com.artech.portfolio.monthlyplanner.repository.MonthlyPlanEntryRepository;
import com.artech.portfolio.monthlyplanner.repository.MonthlyPlanHistoryRepository;
import com.artech.portfolio.monthlyplanner.util.MonthlyPlannerConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MP_V1 Monthly Planner:
 *   #4  recurring earnings/spending carry into the following periods; anything
 *       never entered is zero,
 *   #5  Bond SIP spending is auto-populated and read only,
 *   #2  the Cash-Flow Report projects the selected month plus the next one,
 *   #7  every rule is expressed through MonthlyPlannerConstants.
 */
class MonthlyPlannerServiceImplTest {

    private static final LocalDate NOVEMBER_15 = LocalDate.of(2026, 11, 15);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

    private MonthlyPlanEntryRepository planRepository;
    private MonthlyPlanHistoryRepository historyRepository;
    private BondSipRepository bondSipRepository;
    private MfSipRegistrationRepository mfSipRepository;
    private CreditCardService creditCardService;
    private CurrentUserService currentUserService;
    private MonthlyPlannerServiceImpl service;

    private Client owner;
    private final List<MonthlyPlanEntry> entries = new ArrayList<>();
    private final List<MonthlyPlanHistory> historyRows = new ArrayList<>();
    private final List<BondSip> sips = new ArrayList<>();
    private final List<MfSipRegistration> mfSips = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        planRepository = mock(MonthlyPlanEntryRepository.class);
        historyRepository = mock(MonthlyPlanHistoryRepository.class);
        bondSipRepository = mock(BondSipRepository.class);
        mfSipRepository = mock(MfSipRegistrationRepository.class);
        creditCardService = mock(CreditCardService.class);
        currentUserService = mock(CurrentUserService.class);

        owner = new Client();
        owner.setId(9L);
        owner.setPan("OWNER0001A");
        owner.setName("Owner");
        when(currentUserService.requireClient()).thenReturn(owner);
        when(bondSipRepository.findByClientIdOrderByIdAsc(anyLong())).thenAnswer(inv -> sips);
        when(mfSipRepository.findByClientIdOrderByIdAsc(anyLong())).thenAnswer(inv -> mfSips);

        when(planRepository.save(any(MonthlyPlanEntry.class))).thenAnswer(inv -> {
            MonthlyPlanEntry row = inv.getArgument(0);
            entries.removeIf(existing -> Objects.equals(existing.getPlanMonth(), row.getPlanMonth())
                    && Objects.equals(existing.getCategory(), row.getCategory())
                    && Objects.equals(existing.getClient() == null ? null : existing.getClient().getId(),
                            row.getClient() == null ? null : row.getClient().getId()));
            if (row.getId() == null) {
                row.setId(nextId++);
            }
            entries.add(row);
            return row;
        });
        when(planRepository.findByClientIdAndPlanMonthOrderByCategoryAsc(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> entries.stream()
                        .filter(row -> Objects.equals(row.getPlanMonth(), inv.getArgument(1)))
                        .sorted(Comparator.comparing(MonthlyPlanEntry::getCategory))
                        .toList());
        when(planRepository.findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(
                anyLong(), any(LocalDate.class))).thenAnswer(inv -> entries.stream()
                        .filter(row -> !row.getPlanMonth().isAfter(inv.getArgument(1)))
                        .sorted(Comparator.comparing(MonthlyPlanEntry::getPlanMonth))
                        .toList());

        when(historyRepository.findByClientIdAndPlanMonthOrderByCategoryAsc(
                anyLong(), any(LocalDate.class))).thenAnswer(inv -> historyRows.stream()
                        .filter(row -> Objects.equals(row.getPlanMonth(), inv.getArgument(1)))
                        .sorted(Comparator.comparing(MonthlyPlanHistory::getCategory))
                        .toList());
        when(historyRepository.findByClientIdAndPlanMonthBetweenOrderByPlanMonthAscCategoryAsc(
                anyLong(), any(LocalDate.class), any(LocalDate.class))).thenAnswer(inv -> historyRows.stream()
                        .filter(row -> !row.getPlanMonth().isBefore(inv.getArgument(1))
                                && !row.getPlanMonth().isAfter(inv.getArgument(2)))
                        .toList());
        when(historyRepository.save(any(MonthlyPlanHistory.class))).thenAnswer(inv -> {
            MonthlyPlanHistory row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(nextId++);
                historyRows.add(row);
            }
            return row;
        });

        Clock clock = Clock.fixed(NOVEMBER_15.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new MonthlyPlannerServiceImpl(planRepository, historyRepository, bondSipRepository,
                mfSipRepository, creditCardService, currentUserService, clock, 1);
    }

    // ------------------------------------------------------------- helpers

    private static PlannerLineView line(List<PlannerLineView> lines, String category) {
        return lines.stream().filter(l -> category.equals(l.getCategory())).findFirst().orElseThrow();
    }

    private void save(String month, String category, double amount, boolean recurring, String frequency) {
        save(month, category, amount, recurring, frequency, null, null);
    }

    private void save(String month, String category, double amount, boolean recurring,
                      String frequency, String endDate) {
        save(month, category, amount, recurring, frequency, null, endDate);
    }

    private void save(String month, String category, double amount, boolean recurring,
                      String frequency, String startDate, String endDate) {
        PlannerEntryRequest entry = new PlannerEntryRequest();
        entry.setCategory(category);
        entry.setAmount(amount);
        entry.setRecurring(recurring);
        entry.setFrequency(frequency);
        entry.setStartDate(startDate);
        entry.setEndDate(endDate);
        SavePlannerRequest request = new SavePlannerRequest();
        request.setMonth(month);
        request.setEntries(List.of(entry));
        service.save(request);
    }

    private MfSipRegistration mfSip(double amount, String sipType, String status,
                                    LocalDate start, LocalDate end) {
        MfSipRegistration registration = new MfSipRegistration();
        registration.setClient(owner);
        registration.setSchemeName("Parag Parikh Flexi Cap Fund");
        registration.setRegistrationType(MfSipConstants.TYPE_SIP);
        registration.setSipType(sipType);
        registration.setSipAmount(amount);
        registration.setStatus(status);
        registration.setSipStartDate(start);
        registration.setSipEndDate(end);
        mfSips.add(registration);
        return registration;
    }

    private BondSip bondSip(double amount, String frequency, String status) {
        BondSip sip = new BondSip();
        sip.setClient(owner);
        sip.setIssuer("HDFC Bank");
        sip.setSipDate(LocalDate.of(2026, 1, 10));
        sip.setSipAmount(amount);
        sip.setFrequency(frequency);
        sip.setStatus(status);
        sips.add(sip);
        return sip;
    }

    // ------------------------------------------------------------- #4 reading

    @Test
    void anEmptyMonthIsZeroEverywhere() {
        PlannerMonthView view = service.month(NOVEMBER);

        assertEquals("November 2026", view.getMonthLabel());
        assertEquals(0.0, view.getEarningsTotal());
        assertEquals(0.0, view.getSpendingTotal());
        assertEquals(0.0, view.getNet());
        assertEquals(MonthlyPlannerConstants.EARNINGS.size(), view.getEarnings().size());
        assertEquals(MonthlyPlannerConstants.SPENDING.size(), view.getSpending().size());
        assertTrue(view.getEarnings().stream().allMatch(PlannerLineView::isEditable));
        assertEquals(MonthlyPlannerConstants.FREQUENCIES, view.getFrequencyOptions());
    }

    @Test
    void aRecurringEarningIsCarriedIntoTheNextMonth() {
        save("2026-10", MonthlyPlannerConstants.SALARY, 50000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);

        PlannerLineView salary = line(service.month(NOVEMBER).getEarnings(),
                MonthlyPlannerConstants.SALARY);

        assertEquals(50000.0, salary.getAmount(), "#4: a repeating salary fills the next month");
        assertTrue(salary.isInherited());
        assertEquals(LocalDate.of(2026, 10, 1), salary.getInheritedFrom());
    }

    @Test
    void aOneOffEarningIsNotCarried() {
        save("2026-10", MonthlyPlannerConstants.FREELANCE, 12000, false,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);

        PlannerLineView freelance = line(service.month(NOVEMBER).getEarnings(),
                MonthlyPlannerConstants.FREELANCE);

        assertEquals(0.0, freelance.getAmount(), "#4: freelance must be entered every month");
        assertFalse(freelance.isInherited());
    }

    @Test
    void aQuarterlyEntryOnlyFallsOnItsQuarter() {
        save("2026-01", MonthlyPlannerConstants.LIC, 9000, true,
                MonthlyPlannerConstants.FREQUENCY_QUARTERLY);

        assertEquals(0.0, line(service.month(LocalDate.of(2026, 2, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount());
        assertEquals(0.0, line(service.month(LocalDate.of(2026, 3, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount());
        assertEquals(9000.0, line(service.month(LocalDate.of(2026, 4, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount());
    }

    @Test
    void aWeeklyEntryCountsTheWeekdaysOfTheMonth() {
        // Every Monday 5,000, starting Monday 2 November 2026.
        save("2026-11", MonthlyPlannerConstants.FUEL, 5000, true,
                MonthlyPlannerConstants.FREQUENCY_WEEKLY, "2026-11-02", null);

        PlannerLineView november = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.FUEL);
        // November 2026 has five Mondays: 2, 9, 16, 23 and 30.
        assertEquals(5, november.getOccurrences());
        assertEquals(25000.0, november.getAmount());
        assertEquals(5000.0, november.getEntryAmount(), "the box keeps the weekly figure");

        // December 2026 has four: 7, 14, 21 and 28.
        assertEquals(20000.0, line(service.month(LocalDate.of(2026, 12, 1)).getSpending(),
                MonthlyPlannerConstants.FUEL).getAmount());
    }

    @Test
    void aWeeklyEntryDoesNotCountTheWeeksBeforeItsStartDate() {
        // Started on the third Monday of the month: only 16, 23 and 30 November remain.
        save("2026-11", MonthlyPlannerConstants.FUEL, 5000, true,
                MonthlyPlannerConstants.FREQUENCY_WEEKLY, "2026-11-16", null);

        PlannerLineView fuel = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.FUEL);
        assertEquals(3, fuel.getOccurrences());
        assertEquals(15000.0, fuel.getAmount());
    }

    @Test
    void anExplicitMonthBeatsTheCarriedOverFigure() {
        save("2026-10", MonthlyPlannerConstants.SALARY, 50000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        save("2026-11", MonthlyPlannerConstants.SALARY, 62000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);

        PlannerLineView salary = line(service.month(NOVEMBER).getEarnings(),
                MonthlyPlannerConstants.SALARY);
        assertEquals(62000.0, salary.getAmount(), "the month's own figure wins");
        assertFalse(salary.isInherited());
        assertTrue(salary.isRecurring());
    }

    // ----------------------------------- #4 repeated entries and their end date

    @Test
    void aRepeatingEntryStopsOnItsEndMonth() {
        // Home loan EMI: same amount every month until the client's end month.
        save("2026-10", MonthlyPlannerConstants.HOME_LOAN, 25000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY, "2026-11");

        assertEquals(25000.0, line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.HOME_LOAN).getAmount());
        assertEquals(0.0, line(service.month(LocalDate.of(2026, 12, 1)).getSpending(),
                MonthlyPlannerConstants.HOME_LOAN).getAmount(),
                "the EMI stops on its end month");
    }

    @Test
    void anEndDateCanBeMovedLaterWithoutRetypingTheAmount() {
        save("2026-10", MonthlyPlannerConstants.HOME_LOAN, 25000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY, "2026-11");
        // The client extends the same entry instead of creating a new one.
        save("2026-10", MonthlyPlannerConstants.HOME_LOAN, 25000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY, "2027-01");

        assertEquals(25000.0, line(service.month(LocalDate.of(2026, 12, 1)).getSpending(),
                MonthlyPlannerConstants.HOME_LOAN).getAmount());
        assertEquals(0.0, line(service.month(LocalDate.of(2027, 2, 1)).getSpending(),
                MonthlyPlannerConstants.HOME_LOAN).getAmount());
    }

    @Test
    void theEndDateTravelsWithTheLineForEditing() {
        save("2026-11", MonthlyPlannerConstants.SALARY, 90000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY, "2027-03");

        PlannerLineView salary = line(service.month(NOVEMBER).getEarnings(),
                MonthlyPlannerConstants.SALARY);
        assertEquals(LocalDate.of(2027, 3, 1), salary.getEndDate());
        assertEquals(90000.0, salary.getAmount());
    }

    // ----------------------------------- #4 repeated entries and their start date

    @Test
    void aYearlyEntryCountsOnlyFromItsStartDate() {
        // Entered in November, but the LIC premium is due on 29 January.
        save("2026-11", MonthlyPlannerConstants.LIC, 12000, true,
                MonthlyPlannerConstants.FREQUENCY_YEARLY, "2027-01-29", null);

        PlannerLineView november = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.LIC);
        assertEquals(0.0, november.getAmount(), "a January premium is not a November expense");
        assertFalse(november.isCounted());
        assertEquals(12000.0, november.getEntryAmount(), "the figure is kept for editing");
        assertEquals(LocalDate.of(2027, 1, 29), november.getStartDate());

        assertEquals(0.0, line(service.month(LocalDate.of(2026, 12, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount(), "December is not January either");

        PlannerLineView january = line(service.month(LocalDate.of(2027, 1, 1)).getSpending(),
                MonthlyPlannerConstants.LIC);
        assertTrue(january.isCounted());
        assertEquals(12000.0, january.getAmount());

        assertEquals(0.0, line(service.month(LocalDate.of(2027, 2, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount(), "the premium is yearly, not monthly");
        assertEquals(12000.0, line(service.month(LocalDate.of(2028, 1, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount(), "and it returns next January");
    }

    @Test
    void savingALaterMonthDoesNotBreakAFutureStart() {
        save("2026-11", MonthlyPlannerConstants.LIC, 12000, true,
                MonthlyPlannerConstants.FREQUENCY_YEARLY, "2027-01-29", null);
        // The client presses Save on December without changing the line.
        save("2026-12", MonthlyPlannerConstants.LIC, 12000, true,
                MonthlyPlannerConstants.FREQUENCY_YEARLY, "2027-01-29", null);

        assertEquals(1, entries.size(), "a future-dated entry stays one row");
        assertEquals(12000.0, line(service.month(LocalDate.of(2027, 1, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount());
    }

    @Test
    void savingAnUnchangedCarriedMonthKeepsTheRepeat() {
        save("2026-10", MonthlyPlannerConstants.SALARY, 50000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        // November shows the carried salary; the client saves it as it is.
        save("2026-11", MonthlyPlannerConstants.SALARY, 50000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY, "2026-10-01", null);

        assertEquals(1, entries.size(), "an unchanged carried figure stores no duplicate row");
        assertEquals(50000.0, line(service.month(LocalDate.of(2026, 12, 1)).getEarnings(),
                MonthlyPlannerConstants.SALARY).getAmount());
    }

    @Test
    void aStartDateInThePastAnchorsTheRepeat() {
        // The first premium was paid in August; the client enters it now, in November.
        save("2026-11", MonthlyPlannerConstants.LIC, 9000, true,
                MonthlyPlannerConstants.FREQUENCY_QUARTERLY, "2026-08-29", null);

        PlannerLineView november = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.LIC);
        assertTrue(november.isCounted(), "August + 3 months is November");
        assertEquals(9000.0, november.getAmount());

        assertEquals(0.0, line(service.month(LocalDate.of(2026, 12, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount());
        assertEquals(9000.0, line(service.month(LocalDate.of(2027, 2, 1)).getSpending(),
                MonthlyPlannerConstants.LIC).getAmount());
    }

    @Test
    void anEndBeforeTheStartIsRejected() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                save("2026-11", MonthlyPlannerConstants.LIC, 12000, true,
                        MonthlyPlannerConstants.FREQUENCY_YEARLY, "2027-01-29", "2026-12"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(entries.isEmpty(), "nothing is stored for an impossible window");
    }

    // ------------------------------------------------- #5 auto-populated SIP

    @Test
    void bondSipSpendingIsAutoPopulatedAndReadOnly() {
        bondSip(5000, MonthlyPlannerConstants.FREQUENCY_MONTHLY, "INPROGRESS");
        bondSip(3000, MonthlyPlannerConstants.FREQUENCY_YEARLY, "CREATED");
        bondSip(9000, MonthlyPlannerConstants.FREQUENCY_MONTHLY, "CANCELLED");

        PlannerMonthView view = service.month(NOVEMBER);
        PlannerLineView bondSip = line(view.getSpending(), MonthlyPlannerConstants.BOND_SIP);

        // 5000 monthly + 3000/12 yearly; the cancelled SIP does not count.
        assertEquals(5250.0, bondSip.getAmount());
        assertTrue(bondSip.isAuto());
        assertFalse(bondSip.isEditable());
        assertEquals("/bonds/bond-sip.html", bondSip.getSourceLink());
        assertEquals(5250.0, view.getSpendingTotal());
    }

    @Test
    void savingNeverOverwritesAnAutoPopulatedFigure() {
        bondSip(5000, MonthlyPlannerConstants.FREQUENCY_MONTHLY, "INPROGRESS");
        save("2026-11", MonthlyPlannerConstants.BOND_SIP, 1, false,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);

        assertTrue(entries.isEmpty(), "#5: the SIP line is maintained on its own screen");
        assertEquals(5000.0, line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.BOND_SIP).getAmount());
    }

    @Test
    void mfSipSpendingIsAutoPopulatedFromTheSipRegistration() {
        mfSip(5000, MfSipConstants.MONTHLY, MfSipConstants.STATUS_ACTIVE,
                LocalDate.of(2026, 1, 10), null);
        mfSip(12000, MfSipConstants.QUARTERLY, MfSipConstants.STATUS_ACTIVE,
                LocalDate.of(2026, 1, 10), null);
        mfSip(9000, MfSipConstants.MONTHLY, MfSipConstants.STATUS_CANCELLED,
                LocalDate.of(2026, 1, 10), null);

        PlannerLineView mfSip = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.MF_SIP);

        // 5000 monthly + 12000/3 quarterly; the cancelled SIP does not count.
        assertEquals(9000.0, mfSip.getAmount());
        assertTrue(mfSip.isAuto());
        assertFalse(mfSip.isEditable());
        assertEquals("/mutualfunds/mf-sip-form.html", mfSip.getSourceLink());
    }

    @Test
    void mfSipOutsideItsStartAndEndWindowDoesNotCount() {
        mfSip(5000, MfSipConstants.MONTHLY, MfSipConstants.STATUS_ACTIVE,
                LocalDate.of(2026, 12, 1), null);   // starts later
        mfSip(4000, MfSipConstants.MONTHLY, MfSipConstants.STATUS_ACTIVE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31)); // already ended

        assertEquals(0.0, line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.MF_SIP).getAmount());
    }

    @Test
    void bondSipWithoutRunningSipsStaysManual() {
        PlannerLineView bondSip = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.BOND_SIP);

        assertFalse(bondSip.isAuto());
        assertTrue(bondSip.isEditable());
        assertEquals(0.0, bondSip.getAmount());
    }

    // -------------------------------------------------------- #2/#3 writing

    @Test
    void aMonthIsSavedUpsertedAndTotaled() {
        save("2026-11", MonthlyPlannerConstants.SALARY, 100000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        save("2026-11", MonthlyPlannerConstants.CREDIT_CARD, 20000, false,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);

        PlannerMonthView view = service.month(NOVEMBER);
        assertEquals(100000.0, view.getEarningsTotal());
        assertEquals(20000.0, view.getSpendingTotal());
        assertEquals(80000.0, view.getNet());
        assertEquals(2, entries.size());

        // Saving the same category again updates that month's single row.
        save("2026-11", MonthlyPlannerConstants.SALARY, 120000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        assertEquals(2, entries.size(), "one row per client + month + category");
        assertEquals(100000.0, service.month(NOVEMBER).getNet());
    }

    // -------------------------------------------------------- #2 cash-flow

    @Test
    void cashFlowShowsThisMonthAndTheNext() {
        save("2026-11", MonthlyPlannerConstants.SALARY, 90000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        bondSip(4000, MonthlyPlannerConstants.FREQUENCY_MONTHLY, "INPROGRESS");

        List<PlannerMonthView> months = service.cashFlow(NOVEMBER);

        assertEquals(2, months.size(), "1 month ahead is configured in application.yaml");
        assertEquals("November 2026", months.get(0).getMonthLabel());
        assertEquals("December 2026", months.get(1).getMonthLabel());
        assertEquals(90000.0, months.get(1).getEarningsTotal(), "#4: salary carries forward");
        assertEquals(4000.0, months.get(1).getSpendingTotal());
        assertEquals(86000.0, months.get(1).getNet());
    }

    // ------------------------------------------------------------- helpers

    // ------------------------------------------ #9 one time and Miscellaneous

    @Test
    void aOneTimeEntryIsPlannedInItsOwnMonthOnly() {
        save("2026-11", MonthlyPlannerConstants.FUEL, 5000, true,
                MonthlyPlannerConstants.FREQUENCY_ONE_TIME, "2026-11-20", null);

        PlannerLineView november = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.FUEL);
        assertTrue(november.isCounted());
        assertEquals(5000.0, november.getAmount());
        assertEquals(1, november.getOccurrences(), "a one-time amount is counted once");
        assertFalse(november.isRecurring(), "One time never repeats");
        assertEquals(LocalDate.of(2026, 11, 20), november.getEndDate(),
                "the end date of a one-time entry is its start date");

        assertEquals(0.0, line(service.month(LocalDate.of(2026, 12, 1)).getSpending(),
                MonthlyPlannerConstants.FUEL).getAmount(), "December is not its month");
    }

    @Test
    void aOneTimeEntryDatedInALaterMonthLandsInThatMonth() {
        // Entered in November for a purchase on 15 January.
        save("2026-11", MonthlyPlannerConstants.FREELANCE, 8000, false,
                MonthlyPlannerConstants.FREQUENCY_ONE_TIME, "2027-01-15", null);

        PlannerLineView november = line(service.month(NOVEMBER).getEarnings(),
                MonthlyPlannerConstants.FREELANCE);
        assertEquals(0.0, november.getAmount(), "nothing is earned in November");
        assertFalse(november.isCounted());
        assertEquals(8000.0, november.getEntryAmount(), "the figure is kept for editing");

        PlannerLineView january = line(service.month(LocalDate.of(2027, 1, 1)).getEarnings(),
                MonthlyPlannerConstants.FREELANCE);
        assertTrue(january.isCounted());
        assertEquals(8000.0, january.getAmount());

        assertEquals(0.0, line(service.month(LocalDate.of(2027, 2, 1)).getEarnings(),
                MonthlyPlannerConstants.FREELANCE).getAmount());
    }

    @Test
    void savingTheOneTimeMonthAgainKeepsOneRow() {
        save("2026-11", MonthlyPlannerConstants.FREELANCE, 8000, false,
                MonthlyPlannerConstants.FREQUENCY_ONE_TIME, "2027-01-15", null);
        // January shows the January amount; the client saves January unchanged.
        save("2027-01", MonthlyPlannerConstants.FREELANCE, 8000, false,
                MonthlyPlannerConstants.FREQUENCY_ONE_TIME, "2027-01-15", null);

        assertEquals(1, entries.size(), "a one-time entry stays a single row");
        assertEquals(8000.0, line(service.month(LocalDate.of(2027, 1, 1)).getEarnings(),
                MonthlyPlannerConstants.FREELANCE).getAmount());
    }

    @Test
    void miscellaneousExistsInBothSections() {
        assertTrue(MonthlyPlannerConstants.EARNINGS.containsKey(MonthlyPlannerConstants.MISC_EARNING));
        assertTrue(MonthlyPlannerConstants.SPENDING.containsKey(MonthlyPlannerConstants.MISC_SPENDING));
        assertEquals(MonthlyPlannerConstants.KIND_EARNING,
                MonthlyPlannerConstants.kindOf(MonthlyPlannerConstants.MISC_EARNING));
        assertEquals(MonthlyPlannerConstants.KIND_SPENDING,
                MonthlyPlannerConstants.kindOf(MonthlyPlannerConstants.MISC_SPENDING));

        save("2026-11", MonthlyPlannerConstants.MISC_EARNING, 4000, false,
                MonthlyPlannerConstants.FREQUENCY_ONE_TIME, "2026-11-10", null);
        save("2026-11", MonthlyPlannerConstants.MISC_SPENDING, 1500, false,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);

        PlannerMonthView view = service.month(NOVEMBER);
        assertEquals(4000.0, line(view.getEarnings(), MonthlyPlannerConstants.MISC_EARNING).getAmount());
        assertEquals(1500.0, line(view.getSpending(), MonthlyPlannerConstants.MISC_SPENDING).getAmount());
        assertEquals(4000.0, view.getEarningsTotal());
        assertEquals(1500.0, view.getSpendingTotal());
    }

    // -------------------------------------------- #9 credit card billing cycles

    @Test
    void creditCardBillsArePlannedFromTheirBillingCycle() {
        // The credit card module owns the cycles; the planner only plans the figure.
        when(creditCardService.monthTotal(anyLong(), any(LocalDate.class))).thenReturn(18500.0);

        PlannerLineView creditCard = line(service.month(NOVEMBER).getSpending(),
                MonthlyPlannerConstants.CREDIT_CARD);
        assertTrue(creditCard.isAuto());
        assertFalse(creditCard.isEditable());
        assertEquals(18500.0, creditCard.getAmount());
    }

    // --------------------------------------------------------- #8 history

    @Test
    void everyMonthThatHasHappenedIsCapturedInHistory() {
        save("2026-11", MonthlyPlannerConstants.SALARY, 90000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        save("2026-11", MonthlyPlannerConstants.FUEL, 5000, true,
                MonthlyPlannerConstants.FREQUENCY_WEEKLY, "2026-11-02", null);

        service.month(NOVEMBER);

        assertEquals(MonthlyPlannerConstants.EARNINGS.size()
                        + MonthlyPlannerConstants.SPENDING.size(), historyRows.size(),
                "one row per earning and expense of the month");
        assertEquals(90000.0, historyRows.stream()
                .filter(row -> MonthlyPlannerConstants.SALARY.equals(row.getCategory()))
                .findFirst().orElseThrow().getAmount());
        assertEquals(25000.0, historyRows.stream()
                .filter(row -> MonthlyPlannerConstants.FUEL.equals(row.getCategory()))
                .findFirst().orElseThrow().getAmount(), "the month's own weeks are captured");

        // Opening the month again writes nothing new.
        service.month(NOVEMBER);
        assertEquals(MonthlyPlannerConstants.EARNINGS.size()
                        + MonthlyPlannerConstants.SPENDING.size(), historyRows.size());

        // A future month is a projection, not history.
        service.month(LocalDate.of(2026, 12, 1));
        assertEquals(MonthlyPlannerConstants.EARNINGS.size()
                        + MonthlyPlannerConstants.SPENDING.size(), historyRows.size());
    }

    @Test
    void historyReturnsTheWindowItIsAskedFor() {
        save("2026-11", MonthlyPlannerConstants.SALARY, 90000, true,
                MonthlyPlannerConstants.FREQUENCY_MONTHLY);
        service.month(NOVEMBER);

        var rows = service.history(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        assertEquals(MonthlyPlannerConstants.EARNINGS.size()
                + MonthlyPlannerConstants.SPENDING.size(), rows.size());
        assertTrue(rows.stream().anyMatch(row -> MonthlyPlannerConstants.SALARY
                .equals(row.getCategory()) && row.getAmount() == 90000.0));
        assertEquals("November 2026", rows.get(0).getMonthLabel());
    }

    @Test
    void monthParsingAcceptsBothShapes() {
        assertEquals(NOVEMBER, service.firstOfMonth("2026-11"));
        assertEquals(NOVEMBER, service.firstOfMonth("2026-11-23"));
        assertEquals(LocalDate.of(2026, 11, 1), service.firstOfMonth((LocalDate) null));
        assertNull(MonthlyPlannerConstants.kindOf("NOPE"));
    }

    @Test
    void weeklyOccurrencesCountTheWeekdayOfTheMonth() {
        LocalDate monday = LocalDate.of(2026, 11, 2);

        assertEquals(5, MonthlyPlannerServiceImpl.weeklyOccurrences(monday, NOVEMBER));
        assertEquals(4, MonthlyPlannerServiceImpl.weeklyOccurrences(
                monday, LocalDate.of(2026, 12, 1)));
        assertEquals(1, MonthlyPlannerServiceImpl.weeklyOccurrences(
                LocalDate.of(2026, 11, 30), NOVEMBER), "a late start only counts its own day");
        assertEquals(0, MonthlyPlannerServiceImpl.weeklyOccurrences(
                LocalDate.of(2026, 12, 7), NOVEMBER), "a start in another month does not count yet");
    }
}
