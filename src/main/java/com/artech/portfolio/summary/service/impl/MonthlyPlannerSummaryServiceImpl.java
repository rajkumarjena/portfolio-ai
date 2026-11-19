package com.artech.portfolio.summary.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.monthlyplanner.dto.PlannerLineView;
import com.artech.portfolio.monthlyplanner.dto.PlannerMonthView;
import com.artech.portfolio.monthlyplanner.service.MonthlyPlannerService;
import com.artech.portfolio.monthlyplanner.util.MonthlyPlannerConstants;
import com.artech.portfolio.summary.dto.MonthlyPlannerWidgetRow;
import com.artech.portfolio.summary.dto.MonthlyPlannerWidgetView;
import com.artech.portfolio.summary.service.MonthlyPlannerSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * MP_V4 - the Monthly Planner Summary widget.
 *
 * <p>Everything is computed here, never in the page:</p>
 * <ul>
 *   <li>#4 four columns: Self current month, Self next month, Family current
 *       month, Family next month;</li>
 *   <li>#5 only rows whose amount is greater than zero in at least one of the
 *       four columns appear;</li>
 *   <li>#6 all Miscellaneous rows of a section are added into one row, and the
 *       row is omitted when that total is zero;</li>
 *   <li>#7 Family is the signed-in client plus every associated family member,
 *       exactly like the Bond and Mutual Fund summary widgets;</li>
 *   <li>#8 the DTO is rendered by the same widget card/table styles as the
 *       Mutual Fund card.</li>
 * </ul>
 */
@Service
public class MonthlyPlannerSummaryServiceImpl implements MonthlyPlannerSummaryService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final MonthlyPlannerService monthlyPlannerService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public MonthlyPlannerSummaryServiceImpl(MonthlyPlannerService monthlyPlannerService,
                                            CurrentUserService currentUserService,
                                            Clock clock) {
        this.monthlyPlannerService = monthlyPlannerService;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyPlannerWidgetView getMonthlyPlannerWidget() {
        Client me = currentUserService.currentClient();
        if (me == null || me.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        LocalDate asOf = LocalDate.now(clock);
        LocalDate currentMonth = asOf.withDayOfMonth(1);
        LocalDate nextMonth = currentMonth.plusMonths(1);

        Scope self = collectScope(List.of(me.getId()), currentMonth, nextMonth);
        Scope family = collectScope(familyScope(me), currentMonth, nextMonth);

        MonthlyPlannerWidgetView view = new MonthlyPlannerWidgetView();
        view.setAsOf(asOf);
        view.setCurrentMonth(currentMonth);
        view.setCurrentMonthLabel(currentMonth.format(MONTH_LABEL));
        view.setNextMonth(nextMonth);
        view.setNextMonthLabel(nextMonth.format(MONTH_LABEL));

        view.setEarnings(rows(self.earnings(), family.earnings()));
        view.setSpending(rows(self.spending(), family.spending()));

        view.setSelfEarningsCurrent(total(view.getEarnings(), MonthlyPlannerWidgetRow::getSelfCurrent));
        view.setSelfEarningsNext(total(view.getEarnings(), MonthlyPlannerWidgetRow::getSelfNext));
        view.setFamilyEarningsCurrent(total(view.getEarnings(), MonthlyPlannerWidgetRow::getFamilyCurrent));
        view.setFamilyEarningsNext(total(view.getEarnings(), MonthlyPlannerWidgetRow::getFamilyNext));

        view.setSelfSpendingCurrent(total(view.getSpending(), MonthlyPlannerWidgetRow::getSelfCurrent));
        view.setSelfSpendingNext(total(view.getSpending(), MonthlyPlannerWidgetRow::getSelfNext));
        view.setFamilySpendingCurrent(total(view.getSpending(), MonthlyPlannerWidgetRow::getFamilyCurrent));
        view.setFamilySpendingNext(total(view.getSpending(), MonthlyPlannerWidgetRow::getFamilyNext));

        view.setSelfNetCurrent(round(view.getSelfEarningsCurrent() - view.getSelfSpendingCurrent()));
        view.setSelfNetNext(round(view.getSelfEarningsNext() - view.getSelfSpendingNext()));
        view.setFamilyNetCurrent(round(view.getFamilyEarningsCurrent() - view.getFamilySpendingCurrent()));
        view.setFamilyNetNext(round(view.getFamilyEarningsNext() - view.getFamilySpendingNext()));
        return view;
    }

    /**
     * #7: the client himself plus every associated family member, de-duplicated.
     * With no members linked Family equals Self, as in the other widgets.
     */
    private List<Long> familyScope(Client me) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(me.getId());
        if (me.getFamilyMembers() != null) {
            for (ClientFamilyMember link : me.getFamilyMembers()) {
                Long memberId = link.getMember() == null ? null : link.getMember().getId();
                if (memberId != null) {
                    ids.add(memberId);
                }
            }
        }
        return List.copyOf(ids);
    }

    /**
     * One scope's current/next figures, keyed by planner category. Extra
     * Miscellaneous categories are folded into their section's base category
     * here, so #6 happens before the rows are built.
     */
    private Scope collectScope(List<Long> clientIds, LocalDate currentMonth, LocalDate nextMonth) {
        Map<String, double[]> earnings = new LinkedHashMap<>();
        Map<String, double[]> spending = new LinkedHashMap<>();
        for (Long clientId : clientIds) {
            PlannerMonthView current = monthlyPlannerService.monthForClient(clientId, currentMonth);
            PlannerMonthView next = monthlyPlannerService.monthForClient(clientId, nextMonth);
            add(earnings, current == null ? List.of() : current.getEarnings(), 0);
            add(earnings, next == null ? List.of() : next.getEarnings(), 1);
            add(spending, current == null ? List.of() : current.getSpending(), 0);
            add(spending, next == null ? List.of() : next.getSpending(), 1);
        }
        return new Scope(earnings, spending);
    }

    /** #5/#6: only positive amounts count; every Miscellaneous row uses one key. */
    private static void add(Map<String, double[]> totals, List<PlannerLineView> lines, int index) {
        for (PlannerLineView line : lines) {
            if (line == null || line.getAmount() <= 0) {
                continue;
            }
            String key = line.isMisc()
                    ? MonthlyPlannerConstants.miscBaseFor(line.getKind())
                    : line.getCategory();
            if (key == null || key.isBlank()) {
                continue;
            }
            totals.computeIfAbsent(key, unused -> new double[2])[index] += line.getAmount();
        }
    }

    /** #4/#5: union the Self and Family keys, then drop rows that are zero everywhere. */
    private static List<MonthlyPlannerWidgetRow> rows(Map<String, double[]> self,
                                                      Map<String, double[]> family) {
        Set<String> keys = new LinkedHashSet<>(self.keySet());
        keys.addAll(family.keySet());

        List<MonthlyPlannerWidgetRow> rows = new ArrayList<>();
        for (String key : keys) {
            double[] selfAmounts = self.getOrDefault(key, new double[2]);
            double[] familyAmounts = family.getOrDefault(key, new double[2]);
            if (!positive(selfAmounts) && !positive(familyAmounts)) {
                continue;
            }
            MonthlyPlannerWidgetRow row = new MonthlyPlannerWidgetRow();
            row.setCategory(key);
            row.setLabel(MonthlyPlannerConstants.labelOf(key));
            row.setMisc(MonthlyPlannerConstants.isMiscCategory(key));
            row.setSelfCurrent(round(selfAmounts[0]));
            row.setSelfNext(round(selfAmounts[1]));
            row.setFamilyCurrent(round(familyAmounts[0]));
            row.setFamilyNext(round(familyAmounts[1]));
            rows.add(row);
        }

        rows.sort(Comparator
                .comparing((MonthlyPlannerWidgetRow row) -> row.isMisc() ? 1 : 0)
                .thenComparing(row -> row.getLabel() == null ? "" : row.getLabel(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.getCategory() == null ? "" : row.getCategory()));
        return rows;
    }

    private static boolean positive(double... amounts) {
        for (double amount : amounts) {
            if (amount > 0) {
                return true;
            }
        }
        return false;
    }

    private static double total(List<MonthlyPlannerWidgetRow> rows,
                                ToDoubleFunction<MonthlyPlannerWidgetRow> getter) {
        return round(rows.stream().mapToDouble(getter).sum());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** One scope's category totals for the current and next month. */
    private record Scope(Map<String, double[]> earnings, Map<String, double[]> spending) {
    }
}
