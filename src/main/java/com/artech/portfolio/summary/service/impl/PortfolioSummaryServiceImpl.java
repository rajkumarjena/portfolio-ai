package com.artech.portfolio.summary.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.entity.BondCashFlow;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.repository.CashFlowDataRepository;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import com.artech.portfolio.mf.parser.TxnClassifier;
import com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeValuationRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import com.artech.portfolio.summary.dto.BondWidgetView;
import com.artech.portfolio.summary.dto.MfWidgetRow;
import com.artech.portfolio.summary.dto.MfWidgetView;
import com.artech.portfolio.summary.dto.WidgetRow;
import com.artech.portfolio.summary.service.PortfolioSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // MFR_V11 #2: row captions exactly as the sample Mutual Fund sheet prints them.
    static final String MF_ROW_MONTH = "Current Month Investment";
    static final String MF_ROW_QUARTER = "Current Quarter Investment";
    static final String MF_ROW_YEAR = "Current Year Investment";
    static final String MF_ROW_LAST_YEAR = "Last Year Investment";
    static final String MF_ROW_TOTAL = "Total Capital Invested and Return";

    private final BondRepository bondRepository;
    private final CashFlowDataRepository cashFlowDataRepository;
    private final MfFolioSchemeTransactionRepository mfTransactionRepository;
    private final MfFolioSchemeValuationRepository mfValuationRepository;
    private final MfPortfolioSummaryRepository mfSummaryRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public PortfolioSummaryServiceImpl(BondRepository bondRepository,
                                       CashFlowDataRepository cashFlowDataRepository,
                                       MfFolioSchemeTransactionRepository mfTransactionRepository,
                                       MfFolioSchemeValuationRepository mfValuationRepository,
                                       MfPortfolioSummaryRepository mfSummaryRepository,
                                       CurrentUserService currentUserService,
                                       Clock clock) {
        this.bondRepository = bondRepository;
        this.cashFlowDataRepository = cashFlowDataRepository;
        this.mfTransactionRepository = mfTransactionRepository;
        this.mfValuationRepository = mfValuationRepository;
        this.mfSummaryRepository = mfSummaryRepository;
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

    // =====================================================================
    // MFR_V11 - the Mutual Funds card, next to the Bonds card
    // =====================================================================

    /**
     * MFR_V12 #2-#7: Investment and Market Value per period for Self and Family.
     *
     * <p>The period boundaries follow the sample sheet: the current month, the
     * current calendar quarter and the Indian financial year (1 April .. 31
     * March), plus the previous financial year. The last row is not a period:
     * it is the statement's own PORTFOLIO SUMMARY total (#5).</p>
     */
    @Override
    @Transactional(readOnly = true)
    public MfWidgetView getMfWidget() {
        Client me = currentUserService.currentClient();
        if (me == null || me.getId() == null || me.getPan() == null || me.getPan().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        LocalDate today = LocalDate.now(clock);

        // MFR_V12 #2-#4: the MF ledger is owned by PAN
        // (mf_folio_scheme_transactions.owner_pan), not by client id, so the two
        // scopes are PAN sets: Self = me, Family = me plus every client
        // associated as a family member.
        List<String> selfPans = List.of(me.getPan().trim());
        List<String> familyPans = mfFamilyPans(me);
        MfScope self = loadMfScope(selfPans);
        MfScope family = loadMfScope(familyPans);
        LocalDate monthStart = today.withDayOfMonth(1);
        // First month of the calendar quarter (Apr / Jul / Oct / Jan), day one.
        LocalDate quarterStart = LocalDate.of(today.getYear(),
                ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
        LocalDate fyStart = (today.getMonthValue() >= 4)
                ? LocalDate.of(today.getYear(), 4, 1)
                : LocalDate.of(today.getYear() - 1, 4, 1);

        MfWidgetView view = new MfWidgetView();
        view.setAsOf(today);
        view.getRows().add(mfRow(MF_ROW_MONTH, "MONTH",
                monthStart, today.withDayOfMonth(today.lengthOfMonth()), self, family));
        view.getRows().add(mfRow(MF_ROW_QUARTER, "QUARTER",
                quarterStart, quarterEnd(today), self, family));
        view.getRows().add(mfRow(MF_ROW_YEAR, "FY",
                fyStart, fyStart.plusYears(1).minusDays(1), self, family));
        view.getRows().add(mfRow(MF_ROW_LAST_YEAR, "PREV_FY",
                fyStart.minusYears(1), fyStart.minusDays(1), self, family));
        // MFR_V12 #5: the last row is the statement's printed total, not a
        // ledger figure, so it carries no range (the page prints no dates).
        view.getRows().add(mfTotalRow(loadMfTotals(selfPans), loadMfTotals(familyPans)));
        return view;
    }

    /** PAN of the signed-in client plus the PAN of every associated family member. */
    private List<String> mfFamilyPans(Client me) {
        Set<String> pans = new LinkedHashSet<>();
        if (me.getPan() != null && !me.getPan().isBlank()) {
            pans.add(me.getPan().trim());
        }
        if (me.getFamilyMembers() != null) {
            for (ClientFamilyMember link : me.getFamilyMembers()) {
                Client member = link.getMember();
                String pan = member == null ? null : member.getPan();
                if (pan != null && !pan.isBlank()) {
                    pans.add(pan.trim());
                }
            }
        }
        return List.copyOf(pans);
    }

    /**
     * One owner scope's Mutual Fund data, read with exactly two queries - the
     * ledger and its valuations (MFR_V12 #2):
     * <ul>
     *   <li>the ledger rows themselves, which every row of the widget walks
     *       once instead of running two queries per period;</li>
     *   <li>the NAV per scheme, taken as the MAXIMUM of that scheme's valuation
     *       rows - literally the reference query's {@code MAX(val.nav)}, so a
     *       scheme present in the valuation table always values, and one that
     *       is absent never does (the reference JOIN, not LEFT JOIN).</li>
     * </ul>
     */
    private MfScope loadMfScope(List<String> pans) {
        if (pans.isEmpty()) {
            return new MfScope(List.of(), Map.of());
        }
        List<MfFolioSchemeTransaction> rows = mfTransactionRepository.findByOwnerPanIn(pans);

        Map<Long, BigDecimal> navByScheme = new HashMap<>();
        for (MfFolioSchemeValuation valuation : mfValuationRepository.findByOwnerPanIn(pans)) {
            if (valuation.getSchemeId() == null || valuation.getNav() == null) {
                continue;
            }
            navByScheme.merge(valuation.getSchemeId(), valuation.getNav(), BigDecimal::max);
        }
        return new MfScope(rows, navByScheme);
    }

    /**
     * MFR_V12 #5: the last row of the widget is the statement's own printed
     * total, one "Total" line per PAN: cost value is the investment figure and
     * market value the market value figure. Nothing is recomputed here - with
     * no imported statement the row simply comes back as zero.
     */
    private MfTotals loadMfTotals(List<String> pans) {
        if (pans.isEmpty()) {
            return new MfTotals(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal market = BigDecimal.ZERO;
        for (MfPortfolioSummary summary : mfSummaryRepository.findTotalsByOwnerPanIn(pans)) {
            if (summary.getCostValue() != null) {
                cost = cost.add(summary.getCostValue());
            }
            if (summary.getMarketValue() != null) {
                market = market.add(summary.getMarketValue());
            }
        }
        return new MfTotals(asMoney(cost), asMoney(market));
    }

    /**
     * MFR_V12 #2/#7: Investment = the {@code amount} column of the period's
     * ledger rows, the ADJUSTED rows of a first import excluded. Amounts are
     * signed, so a redemption or switch-out booked inside the same period nets
     * against that period's purchases.
     */
    private BigDecimal investment(MfScope scope, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (MfFolioSchemeTransaction row : scope.rows()) {
            if (row.getAmount() == null || isAdjusted(row)
                    || !inMfPeriod(row.getTxnDate(), from, to)) {
                continue;
            }
            total = total.add(row.getAmount());
        }
        return asMoney(total);
    }

    /**
     * MFR_V12 #2/#7: Market Value = the reference query, scheme by scheme:
     *
     * <pre>
     * SELECT SUM(units * nav) FROM (
     *   SELECT SUM(tran.units) AS units, tran.scheme_id, MAX(val.nav) AS nav
     *   FROM mf_folio_scheme_transactions tran
     *   JOIN mf_folio_schemes_valuation val ON tran.scheme_id = val.scheme_id
     *   WHERE tran.txn_date BETWEEN :from AND :to AND tran.type != 'ADJUSTED'
     *   GROUP BY tran.scheme_id)
     * </pre>
     *
     * <p>So the units are the ones moved IN THE PERIOD (not the holding's
     * closing balance) and each scheme is priced with its own NAV before the
     * per-scheme values are added up.</p>
     */
    private BigDecimal marketValue(MfScope scope, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> unitsByScheme = new HashMap<>();
        for (MfFolioSchemeTransaction row : scope.rows()) {
            if (row.getSchemeId() == null || row.getUnits() == null || isAdjusted(row)
                    || !inMfPeriod(row.getTxnDate(), from, to)) {
                continue;
            }
            unitsByScheme.merge(row.getSchemeId(), row.getUnits(), BigDecimal::add);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : unitsByScheme.entrySet()) {
            BigDecimal nav = scope.navByScheme().get(entry.getKey());
            if (nav == null) {
                continue; // no valuation row - the reference JOIN drops the scheme
            }
            total = total.add(entry.getValue().multiply(nav));
        }
        return asMoney(total);
    }

    /** MFR_V12 #7: the ADJUSTED row is an import bookkeeping line, never a cash flow. */
    private static boolean isAdjusted(MfFolioSchemeTransaction row) {
        return row.getType() != null
                && TxnClassifier.TYPE_ADJUSTED.equalsIgnoreCase(row.getType().trim());
    }

    /** Both bounds inclusive; a row with no date belongs to no period. */
    private static boolean inMfPeriod(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private static BigDecimal asMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private MfWidgetRow mfRow(String label, String period, LocalDate from, LocalDate to,
                              MfScope self, MfScope family) {
        BigDecimal selfInvestment = investment(self, from, to);
        BigDecimal selfMarketValue = marketValue(self, from, to);
        BigDecimal familyInvestment = investment(family, from, to);
        BigDecimal familyMarketValue = marketValue(family, from, to);

        MfWidgetRow row = new MfWidgetRow();
        row.setLabel(label);
        row.setPeriod(period);
        row.setFrom(from);
        row.setTo(to);
        row.setSelfInvestment(selfInvestment);
        row.setSelfMarketValue(selfMarketValue);
        row.setFamilyInvestment(familyInvestment);
        row.setFamilyMarketValue(familyMarketValue);
        // MFR_V12 #6: market value worth less than the money put in is shown red.
        row.setSelfInLoss(selfMarketValue.compareTo(selfInvestment) < 0);
        row.setFamilyInLoss(familyMarketValue.compareTo(familyInvestment) < 0);
        return row;
    }

    /** MFR_V12 #5: the statement's printed total line, with no period at all. */
    private MfWidgetRow mfTotalRow(MfTotals self, MfTotals family) {
        MfWidgetRow row = new MfWidgetRow();
        row.setLabel(MF_ROW_TOTAL);
        row.setPeriod("TOTAL");
        row.setSelfInvestment(self.costValue());
        row.setSelfMarketValue(self.marketValue());
        row.setFamilyInvestment(family.costValue());
        row.setFamilyMarketValue(family.marketValue());
        row.setSelfInLoss(self.marketValue().compareTo(self.costValue()) < 0);
        row.setFamilyInLoss(family.marketValue().compareTo(family.costValue()) < 0);
        return row;
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

    /**
     * MFR_V12 #2: one PAN scope's Mutual Fund data, read once and reused for
     * every row - the ledger itself and the NAV of every scheme that has a
     * valuation row.
     */
    private record MfScope(List<MfFolioSchemeTransaction> rows,
                           Map<Long, BigDecimal> navByScheme) {
    }

    /** MFR_V12 #5: the printed PORTFOLIO SUMMARY total of one PAN scope. */
    private record MfTotals(BigDecimal costValue, BigDecimal marketValue) {
    }
}
