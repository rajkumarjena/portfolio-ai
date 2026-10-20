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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BONDBP_V6 - the widget's numbers are the whole feature, so every rule of the
 * spec is pinned here against a fixed clock:
 *   #4.a active bonds only, net of redemptions already credited back,
 *   #4.c/#4.d and #5 the [today .. calendar end] ranges for week/month/quarter/year,
 *   #4   Family = the signed-in client plus his/her associated family members,
 *   #6   the service computes everything (the page renders verbatim).
 *
 * 20 Oct 2026 is a Tuesday, so "this week" ends on Sunday 25 Oct 2026 - the
 * exact example given in spec point #5.
 */
class PortfolioSummaryServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private BondRepository bondRepository;
    private CashFlowDataRepository cashFlowDataRepository;
    private CurrentUserService currentUserService;
    private PortfolioSummaryServiceImpl service;

    private Client me;

    @BeforeEach
    void setUp() {
        bondRepository = mock(BondRepository.class);
        cashFlowDataRepository = mock(CashFlowDataRepository.class);
        currentUserService = mock(CurrentUserService.class);
        service = new PortfolioSummaryServiceImpl(bondRepository, cashFlowDataRepository,
                currentUserService, fixedClock(TODAY));

        me = new Client();
        me.setId(1L);
        me.setName("Ramesh Kumar");
        me.setPan("ABCDE1234F");
        me.setFamilyMembers(new ArrayList<>());
        when(currentUserService.currentClient()).thenReturn(me);

        // Default: no bonds / no cash flows at all - individual tests override.
        when(bondRepository.findByClientIdIn(anyCollection())).thenReturn(List.of());
        when(cashFlowDataRepository.findAllForClients(anyCollection())).thenReturn(List.of());
    }

    private static Clock fixedClock(LocalDate day) {
        return Clock.fixed(day.atStartOfDay(ZONE).toInstant(), ZONE);
    }

    private static WidgetRow row(BondWidgetView view, String label) {
        return view.getRows().stream()
                .filter(r -> label.equals(r.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing widget row: " + label));
    }

    private static Bond bond(Long id, Client owner, String maturity, double perUnit, int units) {
        Bond b = new Bond();
        b.setId(id);
        b.setClient(owner);
        b.setMaturityDate(maturity == null ? null : LocalDate.parse(maturity));
        b.setInvestmentAmountPerUnit(perUnit);
        b.setUnits(units);
        return b;
    }

    private static BondCashFlow flow(Bond bond, String date, double payout, double amount) {
        BondCashFlow cf = new BondCashFlow();
        cf.setBond(bond);
        cf.setDate(LocalDate.parse(date));
        cf.setPayout(payout);
        cf.setAmount(amount);
        return cf;
    }

    // ----------------------------------------------------------------- #3/#5

    @Test
    void periodRangesRunFromTodayToTheCalendarEndOfEachPeriod() {
        BondWidgetView view = service.getBondWidget();

        assertEquals(TODAY, view.getAsOf());
        assertEquals(5, view.getRows().size());

        WidgetRow total = row(view, "Total Investment");
        assertNull(total.getFrom(), "Total Investment is point-in-time, not a range");
        assertNull(total.getTo());

        assertEquals(TODAY, row(view, "Return This Week").getFrom());
        assertEquals(LocalDate.of(2026, 10, 25), row(view, "Return This Week").getTo(),
                "today Tue 20 Oct -> week ends Sun 25 Oct 2026 (spec #5 example)");
        assertEquals(LocalDate.of(2026, 10, 31), row(view, "Return This Month").getTo());
        assertEquals(LocalDate.of(2026, 12, 31), row(view, "Return This Quarter").getTo());
        assertEquals(LocalDate.of(2026, 12, 31), row(view, "Return This Year").getTo());
    }

    @Test
    void periodEndsFollowTheCalendarQuarterOfTheCurrentDate() {
        // 11 Feb 2026 is a Wednesday: week -> Sun 15 Feb, month -> 28 Feb, quarter -> 31 Mar.
        PortfolioSummaryServiceImpl febService = new PortfolioSummaryServiceImpl(
                bondRepository, cashFlowDataRepository, currentUserService,
                fixedClock(LocalDate.of(2026, 2, 11)));

        BondWidgetView view = febService.getBondWidget();

        assertEquals(LocalDate.of(2026, 2, 15), row(view, "Return This Week").getTo());
        assertEquals(LocalDate.of(2026, 2, 28), row(view, "Return This Month").getTo());
        assertEquals(LocalDate.of(2026, 3, 31), row(view, "Return This Quarter").getTo());
        assertEquals(LocalDate.of(2026, 12, 31), row(view, "Return This Year").getTo());
    }

    // ------------------------------------------------------------------ #4.a

    @Test
    void totalInvestmentCountsActiveBondsOnlyNetOfRedemptionsAlreadyCredited() {
        Bond active = bond(10L, me, "2030-06-30", 1000, 100);     // 1,00,000 invested
        Bond matured = bond(11L, me, "2026-01-10", 500, 40);      // matured -> not active
        Bond noMaturity = bond(12L, me, null, 999, 10);           // no maturity -> not active
        Bond maturesToday = bond(13L, me, "2026-10-20", 700, 7);  // not in the FUTURE -> not active

        when(bondRepository.findByClientIdIn(List.of(1L)))
                .thenReturn(List.of(active, matured, noMaturity, maturesToday));
        when(cashFlowDataRepository.findAllForClients(List.of(1L))).thenReturn(List.of(
                flow(active, "2026-05-01", 0, 20000),   // already credited back -> subtract
                flow(active, "2026-10-23", 0, 1000),    // scheduled after today -> still invested
                flow(matured, "2026-01-10", 0, 20000)   // belongs to an inactive bond -> ignored
        ));

        double expected = 1000 * 100 - 20000; // 80,000
        BondWidgetView view = service.getBondWidget();

        assertEquals(expected, row(view, "Total Investment").getSelf(), 0.001);
        assertEquals(expected, row(view, "Total Investment").getFamily(), 0.001,
                "no family members linked -> Family equals Self");
    }

    // ------------------------------------------------------------------ #4.c/#4.d

    @Test
    void returnsSumInterestPlusRedemptionFromTodayToEachPeriodEnd() {
        Bond bond = bond(20L, me, "2030-01-01", 1000, 1);
        when(bondRepository.findByClientIdIn(List.of(1L))).thenReturn(List.of(bond));
        when(cashFlowDataRepository.findAllForClients(List.of(1L))).thenReturn(List.of(
                flow(bond, "2026-10-19", 999, 0),   // yesterday -> excluded from every period
                flow(bond, "2026-10-20", 100, 50),  // today -> counted everywhere
                flow(bond, "2026-10-25", 200, 0),   // week end -> inclusive
                flow(bond, "2026-10-26", 300, 0),   // past the week, inside the month
                flow(bond, "2026-10-31", 0, 400),   // month end -> inclusive
                flow(bond, "2026-11-01", 500, 0),   // past the month, inside the quarter
                flow(bond, "2026-12-31", 600, 0),   // quarter/year end -> inclusive
                flow(bond, "2027-01-01", 700, 0)    // next year -> excluded
        ));

        BondWidgetView view = service.getBondWidget();

        assertEquals(350, row(view, "Return This Week").getSelf(), 0.001);      // 150 + 200
        assertEquals(1050, row(view, "Return This Month").getSelf(), 0.001);    // + 300 + 400
        assertEquals(2150, row(view, "Return This Quarter").getSelf(), 0.001);  // + 500 + 600
        assertEquals(2150, row(view, "Return This Year").getSelf(), 0.001);
    }

    // -------------------------------------------------------------------- #4

    @Test
    void familyColumnAddsTheBondsOfEveryAssociatedFamilyMember() {
        Client member = new Client();
        member.setId(2L);
        member.setName("Sunita Kumar");
        ClientFamilyMember link = new ClientFamilyMember();
        link.setMember(member);
        me.setFamilyMembers(new ArrayList<>(List.of(link)));

        Bond myBond = bond(100L, me, "2030-06-30", 1000, 10);        // 10,000
        Bond memberBond = bond(200L, member, "2030-06-30", 2000, 5);  // 10,000

        when(bondRepository.findByClientIdIn(List.of(1L))).thenReturn(List.of(myBond));
        when(bondRepository.findByClientIdIn(List.of(1L, 2L))).thenReturn(List.of(myBond, memberBond));
        when(cashFlowDataRepository.findAllForClients(List.of(1L)))
                .thenReturn(List.of(flow(myBond, "2026-10-21", 100, 0)));
        when(cashFlowDataRepository.findAllForClients(List.of(1L, 2L))).thenReturn(List.of(
                flow(myBond, "2026-10-21", 100, 0),
                flow(memberBond, "2026-10-22", 400, 0)));

        BondWidgetView view = service.getBondWidget();

        assertEquals(10000, row(view, "Total Investment").getSelf(), 0.001);
        assertEquals(20000, row(view, "Total Investment").getFamily(), 0.001);
        assertEquals(100, row(view, "Return This Week").getSelf(), 0.001);
        assertEquals(500, row(view, "Return This Week").getFamily(), 0.001);

        // The two scopes really are two scopes: Self = [1], Family = [1, 2].
        verify(cashFlowDataRepository).findAllForClients(List.of(1L));
        verify(cashFlowDataRepository).findAllForClients(List.of(1L, 2L));
    }

    // ------------------------------------------------------------------ #6

    @Test
    void refusesToBuildASummaryWithoutASignedInClient() {
        when(currentUserService.currentClient()).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getBondWidget());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }
}
