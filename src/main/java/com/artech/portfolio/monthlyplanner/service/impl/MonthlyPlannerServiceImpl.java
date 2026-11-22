package com.artech.portfolio.monthlyplanner.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bondsip.entity.BondSip;
import com.artech.portfolio.bondsip.repository.BondSipRepository;
import com.artech.portfolio.bondsip.util.BondSipConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.creditcard.service.CreditCardService;
import com.artech.portfolio.mfsip.entity.MfSipRegistration;
import com.artech.portfolio.mfsip.repository.MfSipRegistrationRepository;
import com.artech.portfolio.mfsip.util.MfSipConstants;
import com.artech.portfolio.monthlyplanner.dto.PlannerEntryRequest;
import com.artech.portfolio.monthlyplanner.dto.PlannerHistoryRowView;
import com.artech.portfolio.monthlyplanner.dto.PlannerLineView;
import com.artech.portfolio.monthlyplanner.dto.PlannerMonthView;
import com.artech.portfolio.monthlyplanner.dto.SavePlannerRequest;
import com.artech.portfolio.monthlyplanner.entity.MonthlyPlanEntry;
import com.artech.portfolio.monthlyplanner.entity.MonthlyPlanHistory;
import com.artech.portfolio.monthlyplanner.repository.MonthlyPlanEntryRepository;
import com.artech.portfolio.monthlyplanner.repository.MonthlyPlanHistoryRepository;
import com.artech.portfolio.monthlyplanner.service.MonthlyPlannerService;
import com.artech.portfolio.monthlyplanner.util.MonthlyPlannerConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * MP_V1 - Monthly Planner, all rules server-side:
 *
 *  #4  An earning entered once can repeat: the row carries a "recording" flag
 *      and a frequency. A month with no row of its own inherits the newest
 *      recurring row of that category whose frequency falls on it (every month,
 *      every 3/6/12 months), and anything that was never entered is zero.
 *  #4  Every entry carries a start date and, when it repeats, an end date. The
 *      repeat is anchored on the start date, so a premium dated 29 January is
 *      not counted in the months before January, and a weekly entry counts the
 *      weekday hits of that month (4 or 5 Mondays - the month's own figure).
 *  #4/#5 Spending is typed in every month (credit card, fuel, shares, PPF,
 *      EPF/NPS, smallcase, home loan EMI, ...) except the SIP-backed
 *      obligations, which are auto-populated from their own module and read only
 *      on the planner - the client adjusts them on the Bond SIP / Mutual Fund
 *      SIP screen.
 *  #7  No magic strings or numbers: categories, labels, recurrence options and
 *      the projection length all come from MonthlyPlannerConstants and
 *      application.yaml.
 *  #8  Every month that has happened is captured in monthly_plan_history - one
 *      row per earning/expense - so analytics can be built from real history
 *      later without replaying the recurrence rules.
 *  #9  "One time" is the frequency of a single dated amount: it exists in the
 *      month of its own date only. Every section also has a Miscellaneous
 *      catch-all for amounts no defined type covers, and the credit card line
 *      is planned from each card's own billing cycle.
 */
@Service
public class MonthlyPlannerServiceImpl implements MonthlyPlannerService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** A Bond SIP contributes to the planner while it is still running. */
    private static final List<String> ACTIVE_BOND_SIP_STATUSES = List.of(
            BondSipConstants.STATUS_CREATED, BondSipConstants.STATUS_INPROGRESS);

    private final MonthlyPlanEntryRepository planRepository;
    private final MonthlyPlanHistoryRepository historyRepository;
    private final BondSipRepository bondSipRepository;
    private final MfSipRegistrationRepository mfSipRepository;
    private final CreditCardService creditCardService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    /** MP_V1 #2: how many months after the selected one the Cash-Flow Report shows. */
    private final int monthsAhead;

    public MonthlyPlannerServiceImpl(MonthlyPlanEntryRepository planRepository,
                                     MonthlyPlanHistoryRepository historyRepository,
                                     BondSipRepository bondSipRepository,
                                     MfSipRegistrationRepository mfSipRepository,
                                     CreditCardService creditCardService,
                                     CurrentUserService currentUserService,
                                     Clock clock,
                                     @Value("${planner.cashflow.months-ahead:1}") int monthsAhead) {
        this.planRepository = planRepository;
        this.historyRepository = historyRepository;
        this.bondSipRepository = bondSipRepository;
        this.mfSipRepository = mfSipRepository;
        this.creditCardService = creditCardService;
        this.currentUserService = currentUserService;
        this.clock = clock;
        this.monthsAhead = Math.max(0, monthsAhead);
    }

    // ---------------------------------------------------------------- reading

    @Override
    @Transactional
    public PlannerMonthView month(LocalDate month) {
        Client me = currentUserService.requireClient();
        LocalDate planMonth = firstOfMonth(month);
        List<MonthlyPlanEntry> history = planRepository
                .findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(me.getId(), planMonth);
        PlannerMonthView view = build(me.getId(), planMonth, history);
        captureHistory(me, view);
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public PlannerMonthView monthForClient(Long clientId, LocalDate month) {
        LocalDate planMonth = firstOfMonth(month);
        List<MonthlyPlanEntry> history = planRepository
                .findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(clientId, planMonth);
        return build(clientId, planMonth, history);
    }

    @Override
    @Transactional
    public List<PlannerMonthView> cashFlow(LocalDate month) {
        Client me = currentUserService.requireClient();
        LocalDate start = firstOfMonth(month);
        LocalDate last = start.plusMonths(monthsAhead);
        // One query for the whole window: each month filters the same history.
        List<MonthlyPlanEntry> history = planRepository
                .findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(me.getId(), last);
        List<PlannerMonthView> months = new ArrayList<>();
        for (int i = 0; i <= monthsAhead; i++) {
            PlannerMonthView view = build(me.getId(), start.plusMonths(i), history);
            captureHistory(me, view); // future months are skipped by the capture itself
            months.add(view);
        }
        return months;
    }

    private PlannerMonthView build(Long clientId, LocalDate month, List<MonthlyPlanEntry> history) {
        Map<String, MonthlyPlanEntry> explicit = new HashMap<>();
        List<MonthlyPlanEntry> upToMonth = new ArrayList<>();
        for (MonthlyPlanEntry row : history) {
            if (row.getPlanMonth() == null || row.getPlanMonth().isAfter(month)) {
                continue;
            }
            upToMonth.add(row);
            if (month.equals(row.getPlanMonth())) {
                explicit.put(row.getCategory(), row);
            }
        }
        Map<String, Double> autoAmounts = autoAmounts(clientId, month);

        PlannerMonthView view = new PlannerMonthView();
        view.setMonth(month);
        view.setMonthLabel(month.format(MONTH_LABEL));
        view.getFrequencyOptions().putAll(MonthlyPlannerConstants.FREQUENCIES);

        for (String category : MonthlyPlannerConstants.EARNINGS.keySet()) {
            view.getEarnings().add(line(category, MonthlyPlannerConstants.KIND_EARNING,
                    month, upToMonth, explicit, autoAmounts.getOrDefault(category, 0.0)));
        }
        for (String category : MonthlyPlannerConstants.SPENDING.keySet()) {
            view.getSpending().add(line(category, MonthlyPlannerConstants.KIND_SPENDING,
                    month, upToMonth, explicit, autoAmounts.getOrDefault(category, 0.0)));
        }
        // MP_V3 #2: append every extra Miscellaneous row saved for this month (or
        // carried into it by its own repeat), then order the screen MP_V3 #3:
        // ascending by label with the Miscellaneous rows last.
        view.getEarnings().addAll(extraMiscLines(MonthlyPlannerConstants.KIND_EARNING,
                month, upToMonth, explicit));
        view.getSpending().addAll(extraMiscLines(MonthlyPlannerConstants.KIND_SPENDING,
                month, upToMonth, explicit));
        sortLines(view.getEarnings());
        sortLines(view.getSpending());

        double earnings = view.getEarnings().stream().mapToDouble(PlannerLineView::getAmount).sum();
        double spending = view.getSpending().stream().mapToDouble(PlannerLineView::getAmount).sum();

        view.setEarningsTotal(round(earnings));
        view.setSpendingTotal(round(spending));
        view.setNet(round(earnings - spending));
        return view;
    }

    /**
     * MP_V3 #2: every additional Miscellaneous row that belongs to the month.
     * The base category is already in the constants map, so only suffixed rows
     * are collected here. A row belongs when it is saved for this month, when
     * its own repeat falls on this month, or when its one-time date is here.
     */
    private List<PlannerLineView> extraMiscLines(String kind, LocalDate month,
                                                 List<MonthlyPlanEntry> upToMonth,
                                                 Map<String, MonthlyPlanEntry> explicit) {
        String base = MonthlyPlannerConstants.miscBaseFor(kind);
        Set<String> categories = new LinkedHashSet<>();
        for (MonthlyPlanEntry row : upToMonth) {
            String category = row.getCategory();
            if (category == null || !MonthlyPlannerConstants.isMiscCategory(category, kind)
                    || base.equals(category)) {
                continue;
            }
            boolean ownMonth = month.equals(row.getPlanMonth());
            boolean oneTimeHere = MonthlyPlannerConstants.FREQUENCY_ONE_TIME
                    .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()))
                    && month.equals(monthOf(startOf(row)));
            if (ownMonth || oneTimeHere || (row.isRecurring() && appliesIn(row, month))) {
                categories.add(category);
            }
        }
        List<PlannerLineView> lines = new ArrayList<>();
        for (String category : categories) {
            lines.add(line(category, kind, month, upToMonth, explicit, 0.0));
        }
        return lines;
    }

    /** MP_V3 #3: labels ascending, every Miscellaneous row after the named ones. */
    private static void sortLines(List<PlannerLineView> lines) {
        lines.sort(Comparator
                .comparing((PlannerLineView line) -> line.isMisc() ? 1 : 0)
                .thenComparing(line -> line.getLabel() == null ? "" : line.getLabel(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(line -> line.getCategory() == null ? "" : line.getCategory()));
    }

    /**
     * MP_V1 #4/#5 - one line of the screen, in priority order:
     * an auto-populated module figure, the figure typed for this month, the
     * newest recurring entry that falls on this month, a repeat that starts in a
     * later month (shown with its figure but not counted yet), or zero.
     */
    private PlannerLineView line(String category, String kind, LocalDate month,
                                 List<MonthlyPlanEntry> upToMonth,
                                 Map<String, MonthlyPlanEntry> explicit,
                                 double autoAmount) {
        PlannerLineView line = new PlannerLineView();
        line.setCategory(category);
        line.setLabel(MonthlyPlannerConstants.labelOf(category));
        line.setKind(kind);
        line.setMisc(MonthlyPlannerConstants.isMiscCategory(category));
        line.setFrequency(MonthlyPlannerConstants.FREQUENCY_DEFAULT);
        line.setEditable(true);
        line.setSourceLink(MonthlyPlannerConstants.SOURCE_LINKS.get(category));
        line.setStartDate(month);
        line.setEndDate(null);

        if (autoAmount > 0) {
            // MP_V1 #5: maintained on its own module, never typed here.
            line.setAmount(round(autoAmount));
            line.setEntryAmount(round(autoAmount));
            line.setAuto(true);
            line.setEditable(false);
            line.setStartDate(null);
            line.setNote("Auto-populated from " + line.getLabel()
                    + (line.getSourceLink() != null
                    ? " - adjust it on that screen."
                    : " - enter the bill of every billing cycle here."));
            // MP_V2 #6: auto-populated rows default to unpaid until the user ticks
            // them; an already stored paid flag (from a previous save) wins.
            MonthlyPlanEntry storedPaid = explicit.get(category);
            line.setPaid(storedPaid != null ? storedPaid.isPaid() : false);
            return line;
        }

        MonthlyPlanEntry row = explicit.get(category);
        if (row != null) {
            return fill(line, row, month);
        }

        // MP_V1 #9: a one-time amount belongs to the month of its own date, even
        // when it was entered from another month's screen.
        MonthlyPlanEntry oneTime = oneTimeIn(upToMonth, category, month);
        if (oneTime != null) {
            fill(line, oneTime, month);
            line.setInherited(true);
            line.setInheritedFrom(oneTime.getPlanMonth());
            return line;
        }

        MonthlyPlanEntry carried = carriedOver(upToMonth, category, month);
        if (carried != null) {
            fill(line, carried, month);
            line.setInherited(true);
            line.setInheritedFrom(carried.getPlanMonth());
            return line;
        }

        // A repeat dated in a later month (the LIC premium in January): keep its
        // figure on the screen, but this month's total does not include it.
        MonthlyPlanEntry upcoming = upcoming(upToMonth, category, month);
        if (upcoming != null) {
            return fill(line, upcoming, month);
        }

        line.setAmount(0);
        line.setEntryAmount(0);
        line.setPaid(true); // MP_V2 #6: a zero amount is always considered paid.
        return line;
    }

    /** Fill a line from the entry that governs it, counted only from its own start date. */
    private PlannerLineView fill(PlannerLineView line, MonthlyPlanEntry row, LocalDate month) {
        LocalDate start = startOf(row);
        boolean started = start == null || !monthOf(start).isAfter(month);
        line.setEntryAmount(round(row.getAmount()));
        line.setAmount(started ? round(monthlyAmount(row, month)) : 0);
        line.setCounted(started);
        line.setOccurrences(started ? occurrences(row, month) : 1);
        line.setRecurring(row.isRecurring());
        line.setFrequency(MonthlyPlannerConstants.frequencyOr(row.getFrequency()));
        line.setStartDate(start);
        line.setEndDate(row.getEndDate());
        line.setNote(row.getNote());
        // MP_V2 #6: paid is tracked per month. A carried-over row belongs to an
        // earlier month, so the current month starts unpaid unless it is its own
        // row or the amount is zero (which is always considered paid).
        boolean ownMonth = row.getPlanMonth() != null && row.getPlanMonth().equals(month);
        boolean paid = ownMonth ? row.isPaid() : (line.getAmount() == 0);
        if (line.getAmount() == 0) {
            paid = true;
        }
        line.setPaid(paid);
        return line;
    }

    /**
     * MP_V1 #4: the newest recurring row of the category that falls on the month,
     * inside its start / end window - a home loan EMI stops by itself on the
     * month the client set, and a yearly premium only lands on its own month.
     */
    static MonthlyPlanEntry carriedOver(List<MonthlyPlanEntry> upToMonth, String category, LocalDate month) {
        for (int i = upToMonth.size() - 1; i >= 0; i--) {
            MonthlyPlanEntry row = upToMonth.get(i);
            if (!category.equals(row.getCategory()) || !row.isRecurring()) {
                continue;
            }
            if (row.getPlanMonth() == null || !row.getPlanMonth().isBefore(month)) {
                continue;
            }
            if (appliesIn(row, month)) {
                return row;
            }
        }
        return null;
    }

    /** The one-time row of the category whose own date falls in this month. */
    static MonthlyPlanEntry oneTimeIn(List<MonthlyPlanEntry> upToMonth, String category,
                                      LocalDate month) {
        for (int i = upToMonth.size() - 1; i >= 0; i--) {
            MonthlyPlanEntry row = upToMonth.get(i);
            if (!category.equals(row.getCategory())) {
                continue;
            }
            if (!MonthlyPlannerConstants.FREQUENCY_ONE_TIME
                    .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()))) {
                continue;
            }
            if (month.equals(monthOf(startOf(row)))) {
                return row;
            }
        }
        return null;
    }

    /** The newest repeated entry of the category whose start date is still ahead of the month. */
    static MonthlyPlanEntry upcoming(List<MonthlyPlanEntry> upToMonth, String category, LocalDate month) {
        for (int i = upToMonth.size() - 1; i >= 0; i--) {
            MonthlyPlanEntry row = upToMonth.get(i);
            if (!category.equals(row.getCategory())) {
                continue;
            }
            boolean oneTime = MonthlyPlannerConstants.FREQUENCY_ONE_TIME
                    .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()));
            if (!row.isRecurring() && !oneTime) {
                continue; // a month-only figure never governs another month
            }
            LocalDate start = startOf(row);
            if (start == null || !monthOf(start).isAfter(month)) {
                continue;
            }
            if (row.getEndDate() != null && monthOf(row.getEndDate()).isBefore(monthOf(start))) {
                continue; // an end before its own start never produces a month
            }
            return row;
        }
        return null;
    }

    /** Does this entry fall on the month - inside its start / end window and on its pattern? */
    static boolean appliesIn(MonthlyPlanEntry row, LocalDate month) {
        LocalDate start = startOf(row);
        if (start == null) {
            return false;
        }
        LocalDate startMonth = monthOf(start);
        if (month.isBefore(startMonth)) {
            return false; // not started yet
        }
        // MP_V1 #9: a one-time amount exists in the month of its own date only.
        if (MonthlyPlannerConstants.FREQUENCY_ONE_TIME
                .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()))) {
            return month.equals(startMonth);
        }
        if (row.getEndDate() != null && month.isAfter(monthOf(row.getEndDate()))) {
            return false; // the repeat ended before this month
        }
        if (MonthlyPlannerConstants.FREQUENCY_WEEKLY
                .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()))) {
            return weeklyOccurrences(row, month) > 0;
        }
        long gap = ChronoUnit.MONTHS.between(startMonth, month);
        return gap % MonthlyPlannerConstants.monthsBetweenRepeats(row.getFrequency()) == 0;
    }

    /**
     * MP_V1 #4: a weekly entry is stored as the figure of one week; the month
     * counts the weeks that actually fall on it (4 or 5 Mondays, say).
     */
    static double monthlyAmount(MonthlyPlanEntry row, LocalDate month) {
        if (MonthlyPlannerConstants.FREQUENCY_WEEKLY
                .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()))) {
            return row.getAmount() * weeklyOccurrences(row, month);
        }
        return row.getAmount();
    }

    /** How many times the entry falls in the month (1 for a monthly/quarterly/... entry). */
    static int occurrences(MonthlyPlanEntry row, LocalDate month) {
        return MonthlyPlannerConstants.FREQUENCY_WEEKLY
                .equals(MonthlyPlannerConstants.frequencyOr(row.getFrequency()))
                ? weeklyOccurrences(row, month) : 1;
    }

    static int weeklyOccurrences(MonthlyPlanEntry row, LocalDate month) {
        LocalDate start = startOf(row);
        return start == null ? 0 : weeklyOccurrences(start, month);
    }

    /**
     * How many times the weekday of {@code from} falls inside the month, never
     * before {@code from} itself - a weekly entry started on the 16th only counts
     * the remaining Mondays of that month.
     */
    static int weeklyOccurrences(LocalDate from, LocalDate month) {
        if (from == null || month == null) {
            return 0;
        }
        LocalDate firstOfMonth = month.withDayOfMonth(1);
        LocalDate earliest = from.isAfter(firstOfMonth) ? from : firstOfMonth;
        if (!YearMonth.from(earliest).equals(YearMonth.from(month))) {
            return 0; // the start date is in another month
        }
        int shift = Math.floorMod(from.getDayOfWeek().getValue()
                - earliest.getDayOfWeek().getValue(),
                MonthlyPlannerConstants.DAYS_IN_WEEK);
        int firstHit = earliest.getDayOfMonth() + shift;
        int length = month.lengthOfMonth();
        if (firstHit > length) {
            return 0;
        }
        return (length - firstHit) / MonthlyPlannerConstants.DAYS_IN_WEEK + 1;
    }

    /** The entry's own start date; rows saved before start dates existed anchor on their month. */
    static LocalDate startOf(MonthlyPlanEntry row) {
        return row.getStartDate() != null ? row.getStartDate() : row.getPlanMonth();
    }

    static LocalDate monthOf(LocalDate date) {
        return date == null ? null : date.withDayOfMonth(1);
    }

    // ------------------------------------------------------- auto-population

    /**
     * MP_V1 #5: the spending figures another module owns for this month. Only
     * categories that actually have data are returned - everything else keeps the
     * client's own monthly figure.
     */
    private Map<String, Double> autoAmounts(Long clientId, LocalDate month) {
        Map<String, Double> amounts = new HashMap<>();
        double bondSip = bondSipMonthlyAmount(clientId, month);
        if (bondSip > 0) {
            amounts.put(MonthlyPlannerConstants.BOND_SIP, bondSip);
        }
        double mfSip = mfSipMonthlyAmount(clientId, month);
        if (mfSip > 0) {
            amounts.put(MonthlyPlannerConstants.MF_SIP, mfSip);
        }
        // MP_V5 #5/#6: every card's transactions billed in this month.
        double creditCards = creditCardService.monthTotal(clientId, month);
        if (creditCards > 0) {
            amounts.put(MonthlyPlannerConstants.CREDIT_CARD, creditCards);
        }
        return amounts;
    }

    /** The month's share of every running Bond SIP (weekly ones count the month's own weeks). */
    private double bondSipMonthlyAmount(Long clientId, LocalDate month) {
        double total = 0;
        for (BondSip sip : bondSipRepository.findByClientIdOrderByIdAsc(clientId)) {
            if (sip == null || sip.getStatus() == null
                    || !ACTIVE_BOND_SIP_STATUSES.contains(sip.getStatus())) {
                continue;
            }
            total += monthlyEquivalent(sip.getSipAmount(), sip.getFrequency(),
                    sip.getSipDate(), month);
        }
        return total;
    }

    /** The month's share of every running Mutual Fund SIP (same monthly-equivalent rule). */
    private double mfSipMonthlyAmount(Long clientId, LocalDate month) {
        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        double total = 0;
        for (MfSipRegistration registration : mfSipRepository.findByClientIdOrderByIdAsc(clientId)) {
            if (registration == null
                    || !MfSipConstants.TYPE_SIP.equals(registration.getRegistrationType())
                    || !MfSipConstants.STATUS_ACTIVE.equals(registration.getStatus())) {
                continue;
            }
            // MF.md 7: the SIP only counts inside its own start / end window.
            if (registration.getSipStartDate() != null
                    && registration.getSipStartDate().isAfter(monthEnd)) {
                continue;
            }
            if (registration.getSipEndDate() != null
                    && registration.getSipEndDate().isBefore(monthStart)) {
                continue;
            }
            total += monthlyEquivalent(registration.getSipAmount(), registration.getSipType(),
                    registration.getSipStartDate(), month);
        }
        return total;
    }

    /**
     * The month's share of a SIP. A weekly SIP counts the weekday hits of the
     * month, exactly like a weekly planner entry; the longer periods are spread
     * evenly over the months they cover.
     */
    private static double monthlyEquivalent(double amount, String frequency,
                                            LocalDate anchor, LocalDate month) {
        return switch (MonthlyPlannerConstants.frequencyOr(frequency)) {
            case MonthlyPlannerConstants.FREQUENCY_WEEKLY ->
                    amount * (anchor != null ? weeklyOccurrences(anchor, month) : weeksInMonth(month));
            case MonthlyPlannerConstants.FREQUENCY_QUARTERLY ->
                    amount / MonthlyPlannerConstants.MONTHS_QUARTERLY;
            case MonthlyPlannerConstants.FREQUENCY_HALF_YEARLY ->
                    amount / MonthlyPlannerConstants.MONTHS_HALF_YEARLY;
            case MonthlyPlannerConstants.FREQUENCY_YEARLY ->
                    amount / MonthlyPlannerConstants.MONTHS_YEARLY;
            default -> amount;
        };
    }

    static double weeksInMonth(LocalDate month) {
        return (double) month.lengthOfMonth() / MonthlyPlannerConstants.DAYS_IN_WEEK;
    }

    // ---------------------------------------------------------------- writing

    @Override
    @Transactional
    public PlannerMonthView save(SavePlannerRequest request) {
        Client me = currentUserService.requireClient();
        LocalDate month = firstOfMonth(request == null ? null : request.getMonth());
        Map<String, MonthlyPlanEntry> existing = new HashMap<>();
        for (MonthlyPlanEntry row : planRepository
                .findByClientIdAndPlanMonthOrderByCategoryAsc(me.getId(), month)) {
            existing.put(row.getCategory(), row);
        }
        // Every row that could govern this month, oldest first.
        List<MonthlyPlanEntry> history = planRepository
                .findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(me.getId(), month);
        Map<String, Double> autoAmounts = autoAmounts(me.getId(), month);
        List<PlannerEntryRequest> entries = request == null || request.getEntries() == null
                ? List.of() : request.getEntries();

        for (PlannerEntryRequest entry : entries) {
            String category = entry.getCategory() == null ? ""
                    : entry.getCategory().trim().toUpperCase(Locale.ENGLISH);
            if (MonthlyPlannerConstants.kindOf(category) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown planner category: " + entry.getCategory());
            }
            boolean auto = autoAmounts.containsKey(category);
            // MP_V1 #5: SIP/credit-card lines are maintained on their own screen.
            // When the client does not express a paid preference (paid == null),
            // leave them untouched so their figure is never overwritten.
            if (auto && entry.getPaid() == null) {
                continue;
            }
            double amount = entry.getAmount() == null ? 0 : entry.getAmount();
            if (auto) {
                amount = autoAmounts.get(category);
            }
            // MP_V2 #6: a zero amount is always considered paid.
            boolean paid = amount == 0 ? true
                    : (entry.getPaid() == null ? false : entry.getPaid());
            if (amount < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Amount cannot be negative for " + MonthlyPlannerConstants.labelOf(category));
            }
            boolean recurring = entry.isRecurring();
            String frequency = MonthlyPlannerConstants.frequencyOr(entry.getFrequency());
            // MP_V1 #9: a one-time entry has a single date and never repeats.
            boolean oneTime = MonthlyPlannerConstants.FREQUENCY_ONE_TIME.equals(frequency);
            recurring = recurring && !oneTime;
            // A blank start date means "from this plan month".
            LocalDate startDate = dateOr(entry.getStartDate(), month);
            // The end of a one-time entry is its start date.
            LocalDate endDate = oneTime ? startDate : dateOr(entry.getEndDate(), null);
            if (endDate != null && monthOf(endDate).isBefore(monthOf(startDate))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "End month cannot be before the start date for "
                                + MonthlyPlannerConstants.labelOf(category));
            }

            MonthlyPlanEntry row = existing.get(category);
            if (row == null) {
                // Saving a month must not break the repeat that fills it: when the
                // screen shows exactly what the running entry already says there is
                // nothing new to store, and a future-dated entry stays a single row.
                MonthlyPlanEntry governing = carriedOver(history, category, month);
                if (governing == null) {
                    governing = oneTimeIn(history, category, month);
                }
                if (governing == null) {
                    governing = upcoming(history, category, month);
                }
                if (governing != null
                        && sameEffect(governing, amount, recurring, frequency, startDate, endDate,
                        paid, entry.getNote())) {
                    continue;
                }
                if (governing == null && !recurring && amount == 0) {
                    continue; // nothing was entered for this category this month
                }
                row = new MonthlyPlanEntry();
                row.setClient(me);                 // CONSISTENCY.md 6 - session, never body
                row.setCategory(category);
                row.setPlanMonth(month);
                existing.put(category, row);
            }
            row.setAmount(round(amount));
            row.setRecurring(recurring);
            row.setFrequency(frequency);
            row.setStartDate(startDate);
            row.setEndDate(oneTime ? startDate : (recurring ? endDate : null));
            row.setSource(MonthlyPlannerConstants.SOURCE_MANUAL);
            row.setNote(entry.getNote());
            row.setPaid(paid);
            planRepository.save(row);
        }

        List<MonthlyPlanEntry> updated = planRepository
                .findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(me.getId(), month);
        PlannerMonthView view = build(me.getId(), month, updated);
        captureHistory(me, view);
        return view;
    }

    // ---------------------------------------------------------------- history

    @Override
    @Transactional(readOnly = true)
    public List<PlannerHistoryRowView> history(LocalDate from, LocalDate to) {
        Client me = currentUserService.requireClient();
        LocalDate currentMonth = LocalDate.now(clock).withDayOfMonth(1);
        LocalDate start = from == null
                ? currentMonth.minusMonths(MonthlyPlannerConstants.MONTHS_IN_HISTORY_WINDOW - 1)
                : firstOfMonth(from);
        LocalDate end = to == null ? currentMonth : firstOfMonth(to);
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The history window ends before it starts");
        }
        return historyRepository
                .findByClientIdAndPlanMonthBetweenOrderByPlanMonthAscCategoryAsc(
                        me.getId(), start, end)
                .stream()
                .map(MonthlyPlannerServiceImpl::historyView)
                .toList();
    }

    /**
     * MP_V1 #8: keep what the month resolved to. A row is written for every
     * category of every month that has happened, and only when its figures
     * changed, so re-opening a month is free and the history stays exact.
     */
    private void captureHistory(Client client, PlannerMonthView view) {
        if (client == null || view == null || view.getMonth() == null) {
            return;
        }
        if (view.getMonth().isAfter(LocalDate.now(clock).withDayOfMonth(1))) {
            return; // a future month is a projection, not history
        }
        Map<String, MonthlyPlanHistory> stored = new HashMap<>();
        for (MonthlyPlanHistory row : historyRepository
                .findByClientIdAndPlanMonthOrderByCategoryAsc(client.getId(), view.getMonth())) {
            stored.put(row.getCategory(), row);
        }
        List<PlannerLineView> lines = new ArrayList<>(view.getEarnings());
        lines.addAll(view.getSpending());
        for (PlannerLineView line : lines) {
            MonthlyPlanHistory row = stored.get(line.getCategory());
            if (row != null && unchanged(row, line)) {
                continue;
            }
            if (row == null) {
                row = new MonthlyPlanHistory();
                row.setClient(client);
                row.setPlanMonth(view.getMonth());
                row.setCategory(line.getCategory());
            }
            row.setKind(line.getKind());
            row.setAmount(round(line.getAmount()));
            row.setEntryAmount(round(line.getEntryAmount()));
            row.setOccurrences(line.getOccurrences());
            row.setFrequency(line.getFrequency());
            row.setRecurring(line.isRecurring());
            row.setPaid(line.isPaid());
            row.setSource(line.isAuto() ? MonthlyPlannerConstants.SOURCE_AUTO
                    : MonthlyPlannerConstants.SOURCE_MANUAL);
            row.setCapturedAt(LocalDateTime.now(clock));
            historyRepository.save(row);
        }
    }

    private static boolean unchanged(MonthlyPlanHistory row, PlannerLineView line) {
        return row.getKind() != null && row.getKind().equals(line.getKind())
                && round(row.getAmount()) == round(line.getAmount())
                && round(row.getEntryAmount()) == round(line.getEntryAmount())
                && row.getOccurrences() == line.getOccurrences()
                && Objects.equals(row.getFrequency(), line.getFrequency())
                && row.isRecurring() == line.isRecurring()
                && row.isPaid() == line.isPaid();
    }

    private static PlannerHistoryRowView historyView(MonthlyPlanHistory row) {
        PlannerHistoryRowView view = new PlannerHistoryRowView();
        view.setMonth(row.getPlanMonth());
        view.setMonthLabel(row.getPlanMonth() == null ? null : row.getPlanMonth().format(MONTH_LABEL));
        view.setCategory(row.getCategory());
        view.setLabel(MonthlyPlannerConstants.labelOf(row.getCategory()));
        view.setKind(row.getKind());
        view.setAmount(row.getAmount());
        view.setEntryAmount(row.getEntryAmount());
        view.setOccurrences(row.getOccurrences());
        view.setFrequency(row.getFrequency());
        view.setRecurring(row.isRecurring());
        view.setPaid(row.isPaid());
        view.setSource(row.getSource());
        return view;
    }

    /** Would storing this figure change what the governing entry already produces? */
    static boolean sameEffect(MonthlyPlanEntry row, double amount, boolean recurring,
                              String frequency, LocalDate startDate, LocalDate endDate,
                              boolean paid, String note) {
        return round(row.getAmount()) == round(amount)
                && row.isRecurring() == recurring
                && MonthlyPlannerConstants.frequencyOr(row.getFrequency()).equals(frequency)
                && Objects.equals(startOf(row), startDate)
                && Objects.equals(monthOf(row.getEndDate()), monthOf(endDate))
                && row.isPaid() == paid
                && Objects.equals(row.getNote(), note);
    }

    // ---------------------------------------------------------------- helpers

    /** The first day of the requested month (current month when none is given). */
    LocalDate firstOfMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(clock).withDayOfMonth(1);
        }
        String value = raw.trim();
        try {
            if (value.length() == MonthlyPlannerConstants.MONTH_PATTERN_LENGTH) {
                return YearMonth.parse(value).atDay(1);
            }
            return LocalDate.parse(value).withDayOfMonth(1);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Month must be in the yyyy-MM format");
        }
    }

    LocalDate firstOfMonth(LocalDate month) {
        return month == null ? LocalDate.now(clock).withDayOfMonth(1)
                : month.withDayOfMonth(1);
    }

    /** A "yyyy-MM-dd" date, a "yyyy-MM" month (its first day) or the fallback when blank. */
    LocalDate dateOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        try {
            if (value.length() == MonthlyPlannerConstants.MONTH_PATTERN_LENGTH) {
                return YearMonth.parse(value).atDay(1);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dates must be in the yyyy-MM-dd (or yyyy-MM) format");
        }
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
