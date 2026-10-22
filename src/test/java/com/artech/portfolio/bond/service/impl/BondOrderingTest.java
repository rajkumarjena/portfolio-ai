package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.util.BondConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.common.controller.AppParamController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BONDBP_V9 #9: the Bond Records screen shows "In Progress" records on top,
 * then Investment Date ascending (same-date ties broken by id, null dates
 * last). The sort happens in the service (server-side, #11) so the paged
 * section listing and the plain list inherit the same order.
 */
class BondOrderingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private BondRepository bondRepository;
    private CurrentUserService currentUserService;
    private BondServiceImpl bondService;

    @BeforeEach
    void setUp() {
        bondRepository = mock(BondRepository.class);
        currentUserService = mock(CurrentUserService.class);
        AppParamController appParamController = mock(AppParamController.class);
        when(appParamController.getConfig()).thenReturn(List.of());
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        bondService = new BondServiceImpl(bondRepository, currentUserService,
                appParamController, clock);
    }

    private Bond bond(Long id, String status, String investmentDate) {
        Bond b = new Bond();
        b.setId(id);
        b.setBondName("B" + id);
        b.setStatus(status);
        b.setInvestmentDate(investmentDate == null ? null : LocalDate.parse(investmentDate));
        return b;
    }

    private static List<Long> ids(List<Bond> bonds) {
        return bonds.stream().map(Bond::getId).toList();
    }

    @Test
    void inProgressRecordsComeFirstThenInvestmentDateAscending() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(bondRepository.findAll()).thenReturn(new ArrayList<>(List.of(
                bond(1L, BondConstants.STATUS_EXECUTED, "2025-01-01"),
                bond(2L, BondConstants.STATUS_IN_PROGRESS, "2025-06-01"),
                bond(3L, BondConstants.STATUS_EXECUTED, "2024-05-05"),
                bond(4L, BondConstants.STATUS_IN_PROGRESS, null),
                bond(5L, BondConstants.STATUS_EXECUTED, "2023-02-02"))));

        List<Bond> result = bondService.getAllBonds();

        assertEquals(List.of(2L, 4L, 5L, 3L, 1L), ids(result),
                "In Progress on top (by investment date, nulls last), then the rest ascending");
        assertEquals(BondConstants.STATUS_IN_PROGRESS, result.get(0).getStatus());
    }

    @Test
    void regularUserGetsSameDefaultOrdering() {
        Client me = new Client();
        me.setId(9L);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireClient()).thenReturn(me);
        when(bondRepository.findByClientId(anyLong())).thenReturn(new ArrayList<>(List.of(
                bond(11L, BondConstants.STATUS_EXECUTED, "2028-07-01"),
                bond(12L, BondConstants.STATUS_EXECUTED, "2024-02-29"),
                bond(13L, BondConstants.STATUS_IN_PROGRESS, "2027-11-15"))));

        List<Bond> result = bondService.getAllBonds();

        assertEquals(List.of(13L, 12L, 11L), ids(result), "In Progress first, then date ascending");
        assertEquals(LocalDate.parse("2024-02-29"), result.get(1).getInvestmentDate());
    }
}
