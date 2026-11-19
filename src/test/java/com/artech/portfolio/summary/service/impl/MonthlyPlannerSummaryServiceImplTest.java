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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MP_V4 - the Monthly Planner Summary widget:
 *   #4 Self/Family for the current and next month,
 *   #5 only positive amounts,
 *   #6 one Miscellaneous row per section,
 *   #7 Family = the client plus every associated family member.
 *
 * 19 Nov 2026 is fixed as "today", so the two columns are November and
 * December 2026.
 */
class MonthlyPlannerSummaryServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
    private static final LocalDate DECEMBER = LocalDate.of(2026, 12, 1);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private MonthlyPlannerService monthlyPlannerService;
    private CurrentUserService currentUserService;
    private MonthlyPlannerSummaryServiceImpl service;
    private Client me;

    @BeforeEach
    void setUp() {
        monthlyPlannerService = mock(MonthlyPlannerService.class);
        currentUserService = mock(CurrentUserService.class);
        service = new MonthlyPlannerSummaryServiceImpl(monthlyPlannerService, currentUserService,
                fixedClock(TODAY));

        me = client(1L, "Ramesh Kumar");
        when(currentUserService.currentClient()).thenReturn(me);
    }

    private static Clock fixedClock(LocalDate day) {
        return Clock.fixed(day.atStartOfDay(ZONE).toInstant(), ZONE);
    }

    private static Client client(Long id, String name) {
        Client client = new Client();
        client.setId(id);
        client.setName(name);
        client.setFamilyMembers(new ArrayList<>());
        return client;
    }

    private static PlannerLineView line(String category, String kind, double amount, boolean misc) {
        PlannerLineView line = new PlannerLineView();
        line.setCategory(category);
        line.setKind(kind);
        line.setLabel(MonthlyPlannerConstants.labelOf(category));
        line.setAmount(amount);
        line.setMisc(misc);
        return line;
    }

    private static PlannerMonthView month(LocalDate month, List<PlannerLineView> earnings,
                                          List<PlannerLineView> spending) {
        PlannerMonthView view = new PlannerMonthView();
        view.setMonth(month);
        view.getEarnings().addAll(earnings);
        view.getSpending().addAll(spending);
        return view;
    }

    private static MonthlyPlannerWidgetRow row(List<MonthlyPlannerWidgetRow> rows, String label) {
        return rows.stream().filter(row -> label.equals(row.getLabel())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing widget row: " + label));
    }

    @Test
    void selfAndFamilyColumnsUseCurrentAndNextMonthAndSkipZeroRows() {
        Client member = client(2L, "Sunita Kumar");
        ClientFamilyMember link = new ClientFamilyMember();
        link.setMember(member);
        me.setFamilyMembers(new ArrayList<>(List.of(link)));

        when(monthlyPlannerService.monthForClient(1L, NOVEMBER)).thenReturn(month(NOVEMBER,
                List.of(line(MonthlyPlannerConstants.SALARY, MonthlyPlannerConstants.KIND_EARNING,
                                100000, false),
                        line(MonthlyPlannerConstants.FREELANCE, MonthlyPlannerConstants.KIND_EARNING,
                                0, false),
                        line(MonthlyPlannerConstants.MISC_EARNING, MonthlyPlannerConstants.KIND_EARNING,
                                100, true),
                        line(MonthlyPlannerConstants.MISC_EARNING + "#2",
                                MonthlyPlannerConstants.KIND_EARNING, 200, true)),
                List.of(line(MonthlyPlannerConstants.CREDIT_CARD,
                                MonthlyPlannerConstants.KIND_SPENDING, 20000, false),
                        line(MonthlyPlannerConstants.MISC_SPENDING,
                                MonthlyPlannerConstants.KIND_SPENDING, 100, true),
                        line(MonthlyPlannerConstants.MISC_SPENDING + "#2",
                                MonthlyPlannerConstants.KIND_SPENDING, 200, true))));
        when(monthlyPlannerService.monthForClient(1L, DECEMBER)).thenReturn(month(DECEMBER,
                List.of(line(MonthlyPlannerConstants.SALARY, MonthlyPlannerConstants.KIND_EARNING,
                                100000, false),
                        line(MonthlyPlannerConstants.SIDE_HUSTLE, MonthlyPlannerConstants.KIND_EARNING,
                                5000, false),
                        line(MonthlyPlannerConstants.MISC_EARNING, MonthlyPlannerConstants.KIND_EARNING,
                                300, true)),
                List.of()));
        when(monthlyPlannerService.monthForClient(2L, NOVEMBER)).thenReturn(month(NOVEMBER,
                List.of(line(MonthlyPlannerConstants.SALARY, MonthlyPlannerConstants.KIND_EARNING,
                                50000, false),
                        line(MonthlyPlannerConstants.MISC_EARNING, MonthlyPlannerConstants.KIND_EARNING,
                                50, true)),
                List.of(line(MonthlyPlannerConstants.FUEL, MonthlyPlannerConstants.KIND_SPENDING,
                                5000, false),
                        line(MonthlyPlannerConstants.MISC_SPENDING,
                                MonthlyPlannerConstants.KIND_SPENDING, 50, true))));
        when(monthlyPlannerService.monthForClient(2L, DECEMBER)).thenReturn(month(DECEMBER,
                List.of(line(MonthlyPlannerConstants.SALARY, MonthlyPlannerConstants.KIND_EARNING,
                        50000, false)),
                List.of(line(MonthlyPlannerConstants.FUEL, MonthlyPlannerConstants.KIND_SPENDING,
                        5000, false))));

        MonthlyPlannerWidgetView view = service.getMonthlyPlannerWidget();

        assertEquals(TODAY, view.getAsOf());
        assertEquals(NOVEMBER, view.getCurrentMonth());
        assertEquals(DECEMBER, view.getNextMonth());
        assertEquals("November 2026", view.getCurrentMonthLabel());
        assertEquals("December 2026", view.getNextMonthLabel());

        assertEquals(3, view.getEarnings().size(),
                "Salary, a next-month Side-Hustle and one Miscellaneous row");
        MonthlyPlannerWidgetRow salary = row(view.getEarnings(), "Salary");
        assertEquals(100000, salary.getSelfCurrent(), 0.001);
        assertEquals(100000, salary.getSelfNext(), 0.001);
        assertEquals(150000, salary.getFamilyCurrent(), 0.001);
        assertEquals(150000, salary.getFamilyNext(), 0.001);

        MonthlyPlannerWidgetRow sideHustle = row(view.getEarnings(), "Side-Hustle Incomes");
        assertEquals(0, sideHustle.getSelfCurrent(), 0.001,
                "a next-month-only earning still gets a row, with zero this month");
        assertEquals(5000, sideHustle.getSelfNext(), 0.001);
        assertEquals(5000, sideHustle.getFamilyNext(), 0.001);

        MonthlyPlannerWidgetRow miscEarning = row(view.getEarnings(), "Miscellaneous");
        assertTrue(miscEarning.isMisc());
        assertEquals(300, miscEarning.getSelfCurrent(), 0.001, "100 + 200 are one row");
        assertEquals(300, miscEarning.getSelfNext(), 0.001);
        assertEquals(350, miscEarning.getFamilyCurrent(), 0.001);

        assertEquals(3, view.getSpending().size());
        MonthlyPlannerWidgetRow creditCard = row(view.getSpending(), "Credit Card Bills");
        assertEquals(20000, creditCard.getSelfCurrent(), 0.001);
        assertEquals(0, creditCard.getSelfNext(), 0.001);
        assertEquals(20000, creditCard.getFamilyCurrent(), 0.001);

        MonthlyPlannerWidgetRow fuel = row(view.getSpending(), "Fuel & Transportation");
        assertEquals(0, fuel.getSelfCurrent(), 0.001, "a family-only row shows zero for Self");
        assertEquals(5000, fuel.getFamilyCurrent(), 0.001);
        assertEquals(5000, fuel.getFamilyNext(), 0.001);

        MonthlyPlannerWidgetRow miscSpending = row(view.getSpending(), "Miscellaneous");
        assertEquals(300, miscSpending.getSelfCurrent(), 0.001);
        assertEquals(350, miscSpending.getFamilyCurrent(), 0.001);

        assertEquals("Miscellaneous", view.getEarnings().get(view.getEarnings().size() - 1).getLabel(),
                "Miscellaneous stays last in its section");
        assertEquals("Miscellaneous", view.getSpending().get(view.getSpending().size() - 1).getLabel());

        assertEquals(100300, view.getSelfEarningsCurrent(), 0.001);
        assertEquals(105300, view.getSelfEarningsNext(), 0.001);
        assertEquals(150350, view.getFamilyEarningsCurrent(), 0.001);
        assertEquals(155300, view.getFamilyEarningsNext(), 0.001);

        assertEquals(20300, view.getSelfSpendingCurrent(), 0.001);
        assertEquals(0, view.getSelfSpendingNext(), 0.001);
        assertEquals(25350, view.getFamilySpendingCurrent(), 0.001);
        assertEquals(5000, view.getFamilySpendingNext(), 0.001);

        assertEquals(80000, view.getSelfNetCurrent(), 0.001);
        assertEquals(105300, view.getSelfNetNext(), 0.001);
        assertEquals(125000, view.getFamilyNetCurrent(), 0.001);
        assertEquals(150300, view.getFamilyNetNext(), 0.001);
    }

    @Test
    void miscellaneousRowIsOmittedWhenEveryMiscellaneousAmountIsZero() {
        when(monthlyPlannerService.monthForClient(1L, NOVEMBER)).thenReturn(month(NOVEMBER,
                List.of(line(MonthlyPlannerConstants.MISC_EARNING, MonthlyPlannerConstants.KIND_EARNING,
                                0, true),
                        line(MonthlyPlannerConstants.MISC_EARNING + "#2",
                                MonthlyPlannerConstants.KIND_EARNING, 0, true)),
                List.of(line(MonthlyPlannerConstants.MISC_SPENDING,
                        MonthlyPlannerConstants.KIND_SPENDING, 0, true))));
        when(monthlyPlannerService.monthForClient(1L, DECEMBER)).thenReturn(month(DECEMBER,
                List.of(), List.of()));

        MonthlyPlannerWidgetView view = service.getMonthlyPlannerWidget();

        assertTrue(view.getEarnings().isEmpty(), "MP_V4 #6.b: zero Miscellaneous never appears");
        assertTrue(view.getSpending().isEmpty());
        assertEquals(0, view.getSelfEarningsCurrent(), 0.001);
        assertEquals(0, view.getFamilySpendingNext(), 0.001);
    }

    @Test
    void refusesToBuildWithoutASignedInClient() {
        when(currentUserService.currentClient()).thenReturn(null);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getMonthlyPlannerWidget());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), error.getStatusCode().value());
        verify(monthlyPlannerService, never()).monthForClient(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(LocalDate.class));
    }
}
