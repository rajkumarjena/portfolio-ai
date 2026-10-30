package com.artech.portfolio.summary.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
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
import com.artech.portfolio.summary.dto.MfWidgetRow;
import com.artech.portfolio.summary.dto.MfWidgetView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MFR_V12 - the Mutual Funds card, corrected calculation. Every rule of the
 * spec is pinned here against a fixed clock (30 Oct 2026):
 *
 * <ul>
 *   <li>#2 Investment = {@code sum(amount)} of the period's ledger rows;</li>
 *   <li>#2 Market Value = {@code sum(units * nav)} where the units are the ones
 *       moved IN THE PERIOD per scheme and the nav is that scheme's
 *       {@code MAX(nav)} from the valuation table - schemes without a valuation
 *       row are dropped, exactly like the reference JOIN;</li>
 *   <li>#7 the ADJUSTED rows of a first import count in neither figure;</li>
 *   <li>#3/#4 the same two figures for Family = the user plus the associated
 *       family members, and for the quarter, the financial year and last year;</li>
 *   <li>#5 the last row is the printed total of {@code mf_portfolio_summary}
 *       where {@code is_total = 1} - cost value as investment, market value as
 *       market value - not a ledger figure;</li>
 *   <li>#6 a market value below the investment is flagged so the page can paint
 *       it red.</li>
 * </ul>
 */
class MfWidgetTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String MY_PAN = "AIGPR6090R";
    private static final String MEMBER_PAN = "BCCPM4525P";

    private MfFolioSchemeTransactionRepository transactions;
    private MfFolioSchemeValuationRepository valuations;
    private MfPortfolioSummaryRepository summaries;
    private CurrentUserService currentUserService;
    private PortfolioSummaryServiceImpl service;
    private Client me;

    @BeforeEach
    void setUp() {
        BondRepository bondRepository = mock(BondRepository.class);
        CashFlowDataRepository cashFlowRepository = mock(CashFlowDataRepository.class);
        transactions = mock(MfFolioSchemeTransactionRepository.class);
        valuations = mock(MfFolioSchemeValuationRepository.class);
        summaries = mock(MfPortfolioSummaryRepository.class);
        currentUserService = mock(CurrentUserService.class);
        service = new PortfolioSummaryServiceImpl(bondRepository, cashFlowRepository,
                transactions, valuations, summaries, currentUserService, fixedClock(TODAY));

        when(bondRepository.findByClientIdIn(anyCollection())).thenReturn(List.of());
        when(cashFlowRepository.findAllForClients(anyCollection())).thenReturn(List.of());
        when(transactions.findByOwnerPanIn(anyCollection())).thenReturn(List.of());
        when(valuations.findByOwnerPanIn(anyCollection())).thenReturn(List.of());
        when(summaries.findTotalsByOwnerPanIn(anyCollection())).thenReturn(List.of());

        me = client(1L, "Raj Kumar Jena", MY_PAN);
        when(currentUserService.currentClient()).thenReturn(me);
    }

    private static Clock fixedClock(LocalDate day) {
        return Clock.fixed(day.atStartOfDay(ZONE).toInstant(), ZONE);
    }

    private static Client client(Long id, String name, String pan) {
        Client client = new Client();
        client.setId(id);
        client.setName(name);
        client.setPan(pan);
        client.setFamilyMembers(new ArrayList<>());
        return client;
    }

    private static MfWidgetRow row(MfWidgetView view, String label) {
        return view.getRows().stream()
                .filter(r -> label.equals(r.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing widget row: " + label));
    }

    /** A plain purchase row (MFR_V12 #2 reference query: everything but ADJUSTED). */
    private static MfFolioSchemeTransaction txn(Long schemeId, String date, String amount, String units) {
        return txn(schemeId, date, TxnClassifier.TYPE_PURCHASE, amount, units, MY_PAN);
    }

    private static MfFolioSchemeTransaction txn(Long schemeId, String date, String type,
                                               String amount, String units, String pan) {
        MfFolioSchemeTransaction txn = new MfFolioSchemeTransaction();
        txn.setSchemeId(schemeId);
        txn.setOwnerPan(pan);
        txn.setType(type);
        txn.setTxnDate(date == null ? null : LocalDate.parse(date));
        txn.setAmount(amount == null ? null : new BigDecimal(amount));
        txn.setUnits(units == null ? null : new BigDecimal(units));
        return txn;
    }

    /** The ADJUSTED line a first import adds (MF.md 10) - never a real cash flow. */
    private static MfFolioSchemeTransaction adjusted(Long schemeId, String date,
                                                    String amount, String units) {
        return txn(schemeId, date, TxnClassifier.TYPE_ADJUSTED, amount, units, MY_PAN);
    }

    private static MfFolioSchemeValuation valuation(Long schemeId, String valueDate, String nav) {
        MfFolioSchemeValuation valuation = new MfFolioSchemeValuation();
        valuation.setSchemeId(schemeId);
        valuation.setOwnerPan(MY_PAN);
        valuation.setValueDate(valueDate == null ? null : LocalDate.parse(valueDate));
        valuation.setNav(nav == null ? null : new BigDecimal(nav));
        return valuation;
    }

    /** The printed "Total" line of a statement (MFR_V12 #5). */
    private static MfPortfolioSummary printedTotal(String pan, String costValue, String marketValue) {
        MfPortfolioSummary summary = new MfPortfolioSummary();
        summary.setOwnerPan(pan);
        summary.setTotal(true);
        summary.setCostValue(costValue == null ? null : new BigDecimal(costValue));
        summary.setMarketValue(marketValue == null ? null : new BigDecimal(marketValue));
        return summary;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    // ---------------------------------------------------------- #2 (rows)

    @Test
    void rowsCoverMonthQuarterFinancialYearsAndThePrintedTotal() {
        MfWidgetView view = service.getMfWidget();

        assertEquals(TODAY, view.getAsOf());
        assertEquals(5, view.getRows().size());
        assertEquals(List.of(
                        "Current Month Investment",
                        "Current Quarter Investment",
                        "Current Year Investment",
                        "Last Year Investment",
                        "Total Capital Invested and Return"),
                view.getRows().stream().map(MfWidgetRow::getLabel).toList());

        MfWidgetRow month = row(view, "Current Month Investment");
        assertEquals(LocalDate.of(2026, 10, 1), month.getFrom());
        assertEquals(LocalDate.of(2026, 10, 31), month.getTo());

        MfWidgetRow quarter = row(view, "Current Quarter Investment");
        assertEquals(LocalDate.of(2026, 10, 1), quarter.getFrom());
        assertEquals(LocalDate.of(2026, 12, 31), quarter.getTo());

        // The Indian financial year runs 1 April .. 31 March (sample sheet).
        MfWidgetRow year = row(view, "Current Year Investment");
        assertEquals(LocalDate.of(2026, 4, 1), year.getFrom());
        assertEquals(LocalDate.of(2027, 3, 31), year.getTo());

        MfWidgetRow lastYear = row(view, "Last Year Investment");
        assertEquals(LocalDate.of(2025, 4, 1), lastYear.getFrom());
        assertEquals(LocalDate.of(2026, 3, 31), lastYear.getTo());

        // #5: the printed total is a point-in-time figure, so it has no range.
        MfWidgetRow total = row(view, "Total Capital Invested and Return");
        assertNull(total.getFrom(), "the total is the statement's own total, not a range");
        assertNull(total.getTo());
    }

    @Test
    void financialYearStartsInAprilEvenInTheFirstQuarterOfTheCalendarYear() {
        PortfolioSummaryServiceImpl january = new PortfolioSummaryServiceImpl(
                mock(BondRepository.class), mock(CashFlowDataRepository.class),
                transactions, valuations, summaries, currentUserService,
                fixedClock(LocalDate.of(2027, 2, 11)));

        MfWidgetView view = january.getMfWidget();

        assertEquals(LocalDate.of(2026, 4, 1), row(view, "Current Year Investment").getFrom());
        assertEquals(LocalDate.of(2027, 3, 31), row(view, "Current Year Investment").getTo());
        assertEquals(LocalDate.of(2025, 4, 1), row(view, "Last Year Investment").getFrom());
        // 11 Feb 2027 -> current quarter is Jan .. Mar, current month February.
        assertEquals(LocalDate.of(2027, 1, 1), row(view, "Current Quarter Investment").getFrom());
        assertEquals(LocalDate.of(2027, 3, 31), row(view, "Current Quarter Investment").getTo());
        assertEquals(LocalDate.of(2027, 2, 28), row(view, "Current Month Investment").getTo());
    }

    // ------------------------------------------------------------ #2/#7 invest

    @Test
    void investmentSumsTheAmountColumnAndIgnoresTheAdjustedRows() {
        // Mid-quarter clock so month, quarter and financial year really differ.
        PortfolioSummaryServiceImpl midQuarter = new PortfolioSummaryServiceImpl(
                mock(BondRepository.class), mock(CashFlowDataRepository.class),
                transactions, valuations, summaries, currentUserService,
                fixedClock(LocalDate.of(2026, 11, 15)));

        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(1L, "2026-10-05", "10000", "100"),          // this quarter, not this month
                txn(1L, "2026-11-10", TxnClassifier.TYPE_PURCHASE_SIP, "5000", "50", MY_PAN),
                txn(1L, "2026-11-12", TxnClassifier.TYPE_REDEMPTION, "-3000", "-30", MY_PAN),
                adjusted(1L, "2026-11-01", "999999", "0"),      // #7: never counted
                txn(1L, "2026-09-15", "4000", "40"),            // this financial year only
                txn(1L, "2026-04-01", "20000", "200"),          // this financial year
                txn(1L, "2025-06-01", "7000", "70"),            // last financial year
                txn(1L, null, "1000", "10")));                  // undated -> no period at all

        MfWidgetView view = midQuarter.getMfWidget();

        assertMoney("2000", row(view, "Current Month Investment").getSelfInvestment());
        assertMoney("12000", row(view, "Current Quarter Investment").getSelfInvestment());
        assertMoney("36000", row(view, "Current Year Investment").getSelfInvestment());
        assertMoney("7000", row(view, "Last Year Investment").getSelfInvestment());
    }

    // ------------------------------------------------------- #2 market value

    @Test
    void marketValueUsesTheUnitsOfThePeriodTimesEachSchemesOwnNav() {
        // Scheme 11: 100 units in October plus 40 in November = 140 units in the
        // quarter; its 2025 units are NOT part of this period's figure.
        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(11L, "2025-05-01", "900", "10"),
                txn(11L, "2026-10-02", "10000", "100"),
                txn(11L, "2026-11-12", "5000", "40"),
                txn(12L, "2026-10-08", "2000", "50")));
        when(valuations.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                valuation(11L, "2026-10-24", "12.5"),
                valuation(12L, "2026-10-24", "40")));

        MfWidgetRow quarter = row(service.getMfWidget(), "Current Quarter Investment");

        // 140 x 12.5 = 1,750 plus 50 x 40 = 2,000 (each scheme priced separately)
        assertMoney("3750", quarter.getSelfMarketValue());
    }

    @Test
    void marketValueDropsSchemesThatHaveNoValuationRow() {
        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(21L, "2026-10-02", "1000", "10"),
                txn(22L, "2026-10-02", "1000", "5")));   // no valuation -> reference JOIN drops it
        when(valuations.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(valuation(21L, "2026-10-24", "12")));

        MfWidgetRow month = row(service.getMfWidget(), "Current Month Investment");

        assertMoney("120", month.getSelfMarketValue());
    }

    @Test
    void adjustedRowsAddNeitherUnitsNorAmountToTheMarketValue() {
        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(31L, "2026-10-02", "100", "10"),
                adjusted(31L, "2026-10-03", "5000", "90")));   // #7: excluded
        when(valuations.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(valuation(31L, "2026-10-24", "10")));

        MfWidgetRow month = row(service.getMfWidget(), "Current Month Investment");

        assertMoney("100", month.getSelfInvestment());
        assertMoney("100", month.getSelfMarketValue());   // 10 units, not 100
    }

    @Test
    void marketValueTakesTheSchemesMaximumNavLikeTheReferenceQuery() {
        when(transactions.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(txn(41L, "2026-10-02", "1000", "10")));
        when(valuations.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                valuation(41L, "2025-12-31", "5"),
                valuation(41L, "2026-10-24", "12"),
                valuation(41L, "2026-06-30", "9")));      // MAX(nav) = 12

        assertMoney("120", row(service.getMfWidget(), "Current Month Investment").getSelfMarketValue());
    }

    // ---------------------------------------------------------------- #5 total

    @Test
    void totalRowIsThePrintedPortfolioSummaryTotalNotALedgerFigure() {
        when(summaries.findTotalsByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(printedTotal(MY_PAN, "3294237.30", "5314530.96")));
        // Any ledger rows at all must not influence the total row.
        when(transactions.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(txn(51L, "2026-10-02", "1000", "10")));

        MfWidgetRow total = row(service.getMfWidget(), "Total Capital Invested and Return");

        assertMoney("3294237.30", total.getSelfInvestment());
        assertMoney("5314530.96", total.getSelfMarketValue());
        assertFalse(total.isSelfInLoss());
    }

    @Test
    void totalRowSumsOnePrintedTotalPerPanOfTheScope() {
        Client member = client(2L, "Sonalika Mohapatra", MEMBER_PAN);
        ClientFamilyMember link = new ClientFamilyMember();
        link.setMember(member);
        me.setFamilyMembers(new ArrayList<>(List.of(link)));

        when(summaries.findTotalsByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(printedTotal(MY_PAN, "1000", "900")));
        when(summaries.findTotalsByOwnerPanIn(List.of(MY_PAN, MEMBER_PAN))).thenReturn(List.of(
                printedTotal(MY_PAN, "1000", "900"),
                printedTotal(MEMBER_PAN, "500", null)));   // a statement with no market value yet

        MfWidgetRow total = row(service.getMfWidget(), "Total Capital Invested and Return");

        assertMoney("1000", total.getSelfInvestment());
        assertMoney("900", total.getSelfMarketValue());
        assertMoney("1500", total.getFamilyInvestment());
        assertMoney("900", total.getFamilyMarketValue());
    }

    // ------------------------------------------------------ #3/#4 family side

    @Test
    void familyColumnAggregatesTheLedgerOfEveryAssociatedMember() {
        Client member = client(2L, "Sonalika Mohapatra", MEMBER_PAN);
        ClientFamilyMember link = new ClientFamilyMember();
        link.setMember(member);
        me.setFamilyMembers(new ArrayList<>(List.of(link)));

        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(51L, "2026-10-02", "1000", "10")));
        when(transactions.findByOwnerPanIn(List.of(MY_PAN, MEMBER_PAN))).thenReturn(List.of(
                txn(51L, "2026-10-02", "1000", "10"),
                txn(52L, "2026-10-03", "3000", "30"),
                // The same scheme held by both PANs: the units add up first (10 + 10 = 20).
                txn(53L, "2026-10-04", "100", "10"),
                txn(53L, "2026-10-05", TxnClassifier.TYPE_PURCHASE_SIP, "100", "10", MEMBER_PAN)));
        when(valuations.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                valuation(51L, "2026-10-24", "10"),
                valuation(53L, "2026-10-24", "2")));
        when(valuations.findByOwnerPanIn(List.of(MY_PAN, MEMBER_PAN))).thenReturn(List.of(
                valuation(51L, "2026-10-24", "10"),
                valuation(52L, "2026-10-24", "100"),
                valuation(53L, "2026-10-24", "2")));

        MfWidgetRow month = row(service.getMfWidget(), "Current Month Investment");

        assertMoney("1000", month.getSelfInvestment());
        assertMoney("100", month.getSelfMarketValue());          // 10 x 10
        assertMoney("4200", month.getFamilyInvestment());
        assertMoney("3140", month.getFamilyMarketValue());       // 100 + 3000 + (20x2)
    }

    @Test
    void familyEqualsSelfWhenNoFamilyMemberIsAssociated() {
        when(transactions.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(txn(61L, "2026-10-02", "2500", "25")));
        when(valuations.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(valuation(61L, "2026-10-24", "8")));

        MfWidgetRow month = row(service.getMfWidget(), "Current Month Investment");

        assertMoney("2500", month.getSelfInvestment());
        assertMoney("2500", month.getFamilyInvestment());
        assertMoney("200", month.getSelfMarketValue());
        assertMoney("200", month.getFamilyMarketValue());
    }

    // ---------------------------------------------------------------- #6 red

    @Test
    void marketValueIsFlaggedWhenItIsWorthLessThanTheMoneyPutIn() {
        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(71L, "2026-10-02", "1000", "10")));     // 1,000 in ...
        when(valuations.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(valuation(71L, "2026-10-24", "90")));  // ... worth 900

        MfWidgetRow month = row(service.getMfWidget(), "Current Month Investment");

        assertMoney("900", month.getSelfMarketValue());
        assertTrue(month.isSelfInLoss(), "market value below investment must be painted red");
        assertTrue(month.isFamilyInLoss());
    }

    @Test
    void marketValueIsNotFlaggedWhenItIsWorthAtLeastTheInvestment() {
        when(transactions.findByOwnerPanIn(List.of(MY_PAN))).thenReturn(List.of(
                txn(72L, "2026-10-02", "1000", "10")));
        when(valuations.findByOwnerPanIn(List.of(MY_PAN)))
                .thenReturn(List.of(valuation(72L, "2026-10-24", "100")));  // exactly 1,000

        MfWidgetRow month = row(service.getMfWidget(), "Current Month Investment");

        assertFalse(month.isSelfInLoss(), "equal value is not a loss");
        assertFalse(month.isFamilyInLoss());
    }

    @Test
    void refusesToBuildTheWidgetWithoutASignedInClient() {
        when(currentUserService.currentClient()).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getMfWidget());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }
}
