package com.artech.portfolio.summary.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.entity.BondCashFlow;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.repository.CashFlowDataRepository;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.summary.dto.BondWidgetView;
import com.artech.portfolio.summary.dto.WidgetRow;
import com.artech.portfolio.summary.service.PortfolioSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * BONDBP_V6 - Portfolio Summary widget, Bonds card.
 *
 * Spec, point by point:
 *  #4   "Self" is the logged-in user's figure; "Family" is the sum over the
 *       user AND every client associated to him/her as a family member
 *       (BONDBP_V5 #4 associations).
 *  #4.a Total Bond Investment = what is still invested in the bonds that are
 *       ACTIVE today (maturity date in the future), i.e. the invested amount
 *       minus the redemptions already credited back (visible on the Bond
 *       Returns Report as the Redemption column).
 *  #4.b The Family figure runs the same calculation over the wider scope.
 *  #4.c Return This Week = interest (payout) + redemption (amount) falling
 *       between the current date and the end of the calendar week.
 *  #4.d Month / Quarter / Year work identically with their calendar ends.
 *  #5   ranges are computed here, e.g. today .. 25 Oct 2026 for the week.
 *  #6   every calculation lives on the server; the page only renders.
 */
@Service
public class PortfolioSummaryServiceImpl implements PortfolioSummaryService {

    // Row captions from the sample widget report (BONDBP_V6 #3) - kept here so
    // the API stays the single source the UI renders.
    static final String ROW_RETURN_WEEK = "Weekly Return Due";
    static final String ROW_RETURN_MONTH = "Monthly Return Due";
    static final String ROW_RETURN_QUARTER = "Quarterly Return Due";
    static final String ROW_RETURN_YEAR = "Yearly Return Due";
    static final String ROW_TOTAL_INVESTMENT = "Total Capital Invested";
    // BONDBP_V12 #1/#2: interest already realised - every past payout up to today.
    static final String ROW_TOTAL_REALISED = "Total Realised Return";

    private final BondRepository bondRepository;
    private final CashFlowDataRepository cashFlowDataRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public PortfolioSummaryServiceImpl(BondRepository bondRepository,
                                       CashFlowDataRepository cashFlowDataRepository,
                                       CurrentUserService currentUserService,
                                       Clock clock) {
        this.bondRepository = bondRepository;
        this.cashFlowDataRepository = cashFlowDataRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public BondWidgetView getBondWidget() {
        // #4: everything is relative to the signed-in user - never a client id
        // from the request (CONSISTENCY.md 6).
        Client me = currentUserService.currentClient();
        if (me == null || me.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        LocalDate today = LocalDate.now(clock);

        Scope selfScope = loadScope(List.of(me.getId()));
        Scope familyScope = loadScope(familyScope(me));

        double selfInvestment = totalInvestment(selfScope, today);
        double familyInvestment = totalInvestment(familyScope, today);

        // #5: "from current date till the calendar end of the period".
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate quarterEnd = quarterEnd(today);
        LocalDate yearEnd = LocalDate.of(today.getYear(), 12, 31);

        BondWidgetView view = new BondWidgetView();
        view.setAsOf(today);
        view.getRows().add(row(ROW_RETURN_WEEK, "WEEK", today, weekEnd,
                returnBetween(selfScope, today, weekEnd),
                returnBetween(familyScope, today, weekEnd)));
        view.getRows().add(row(ROW_RETURN_MONTH, "MONTH", today, monthEnd,
                returnBetween(selfScope, today, monthEnd),
                returnBetween(familyScope, today, monthEnd)));
        view.getRows().add(row(ROW_RETURN_QUARTER, "QUARTER", today, quarterEnd,
                returnBetween(selfScope, today, quarterEnd),
                returnBetween(familyScope, today, quarterEnd)));
        view.getRows().add(row(ROW_RETURN_YEAR, "YEAR", today, yearEnd,
                returnBetween(selfScope, today, yearEnd),
                returnBetween(familyScope, today, yearEnd)));
        view.getRows().add(row(ROW_TOTAL_INVESTMENT, "INVESTMENT", null, null,
                selfInvestment, familyInvestment));
        // BONDBP_V12 #2: interest already realised - sum of the cash-flow payout
        // column for every payment dated up to today (no lower bound), across
        // the client and his/her associated family members. Redemption (amount)
        // is deliberately excluded: it is a return of capital, not interest.
        view.getRows().add(row(ROW_TOTAL_REALISED, "REALISED", null, today,
                realisedReturn(selfScope, today),
                realisedReturn(familyScope, today)));
        return view;
    }

    /**
     * BONDBP_V6 #4: the Family scope is the client himself plus every client
     * he/she has associated as a family member (BONDBP_V5 #4 links), de-duped.
     * With no members linked the Family column simply equals Self.
     */
    private List<Long> familyScope(Client me) {
        List<Long> ids = new ArrayList<>();
        ids.add(me.getId());
        if (me.getFamilyMembers() != null) {
            for (ClientFamilyMember link : me.getFamilyMembers()) {
                Long memberId = link.getMember() == null ? null : link.getMember().getId();
                if (memberId != null && !ids.contains(memberId)) {
                    ids.add(memberId);
                }
            }
        }
        return ids;
    }

    /** Both widget columns come from these two queries per scope - no lazy N+1 (#6). */
    private Scope loadScope(List<Long> clientIds) {
        return new Scope(
                bondRepository.findByClientIdIn(clientIds),
                cashFlowDataRepository.findAllForClients(clientIds));
    }

    /**
     * BONDBP_V6 #4.a: current investment on the bonds that are ACTIVE today
     * (maturity date in the future from the current date), i.e. what was
     * invested minus the redemptions already credited back to the client.
     * Bonds with no maturity date or one that has passed are not counted.
     */
    private double totalInvestment(Scope scope, LocalDate today) {
        double invested = 0;
        double creditedBack = 0;
        for (Bond bond : scope.bonds()) {
            if (bond.getMaturityDate() == null || !bond.getMaturityDate().isAfter(today)) {
                continue; // not an active bond
            }
            invested += bond.getInvestmentAmountPerUnit() * bond.getUnits();
            creditedBack += redemptionCreditedBack(scope, bond, today);
        }
        return invested - creditedBack;
    }

    /** Redemptions (Bond Returns Report "Redemption" column) dated up to today. */
    private double redemptionCreditedBack(Scope scope, Bond bond, LocalDate today) {
        return scope.cashFlows().stream()
                .filter(cf -> cf.getDate() != null && !cf.getDate().isAfter(today))
                .filter(cf -> cf.getBond() != null && bond.getId().equals(cf.getBond().getId()))
                .mapToDouble(BondCashFlow::getAmount)
                .sum();
    }

    /**
     * BONDBP_V6 #4.c/#4.d: interest (payout) + redemption (amount) incurred
     * between the current date and the end of the period - both ends inclusive.
     */
    private double returnBetween(Scope scope, LocalDate from, LocalDate to) {
        return scope.cashFlows().stream()
                .filter(cf -> cf.getDate() != null
                        && !cf.getDate().isBefore(from) && !cf.getDate().isAfter(to))
                .mapToDouble(cf -> cf.getPayout() + cf.getAmount())
                .sum();
    }

    /**
     * BONDBP_V12 #2: "Total Realised Return" - the sum of the interest payout
     * column for every cash-flow record whose payment date falls in the past
     * up to today (unbounded below, capped at today). Redemption (amount) is
     * excluded because it is a return of capital, not an interest return.
     */
    private double realisedReturn(Scope scope, LocalDate today) {
        return scope.cashFlows().stream()
                .filter(cf -> cf.getDate() != null && !cf.getDate().isAfter(today))
                .mapToDouble(BondCashFlow::getPayout)
                .sum();
    }

    /** Last day of the calendar quarter containing today (BONDBP_V6 #4.d/#5). */
    private static LocalDate quarterEnd(LocalDate today) {
        int lastMonth = ((today.getMonthValue() - 1) / 3) * 3 + 3; // 3, 6, 9 or 12
        LocalDate firstOfLastMonth = LocalDate.of(today.getYear(), lastMonth, 1);
        return firstOfLastMonth.withDayOfMonth(firstOfLastMonth.lengthOfMonth());
    }

    private static WidgetRow row(String label, String period, LocalDate from, LocalDate to,
                                 double self, double family) {
        WidgetRow row = new WidgetRow();
        row.setLabel(label);
        row.setPeriod(period);
        row.setFrom(from);
        row.setTo(to);
        row.setSelf(self);
        row.setFamily(family);
        return row;
    }

    /** One owner scope's data, loaded once and reused for all rows. */
    private record Scope(List<Bond> bonds, List<BondCashFlow> cashFlows) {
    }
}
