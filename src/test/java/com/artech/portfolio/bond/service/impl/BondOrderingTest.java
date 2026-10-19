package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.client.entity.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BONDBP_V4 #2: the Bond Records screen defaults to Maturity Date order.
 * The sort happens in the service (server-side, BONDBP_V4 #5) so both the
 * ADMIN path (all bonds) and the per-user path inherit it.
 */
class BondOrderingTest {

    private BondRepository bondRepository;
    private CurrentUserService currentUserService;
    private BondServiceImpl bondService;

    @BeforeEach
    void setUp() {
        bondRepository = mock(BondRepository.class);
        currentUserService = mock(CurrentUserService.class);
        bondService = new BondServiceImpl(bondRepository, currentUserService);
    }

    private Bond bond(Long id, String maturity) {
        Bond b = new Bond();
        b.setId(id);
        b.setBondName("B" + id);
        b.setMaturityDate(maturity == null ? null : LocalDate.parse(maturity));
        return b;
    }

    private static List<Long> ids(List<Bond> bonds) {
        return bonds.stream().map(Bond::getId).toList();
    }

    @Test
    void adminSeesBondsOrderedByMaturityDateAscendingWithNullsLast() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(bondRepository.findAll()).thenReturn(new ArrayList<>(List.of(
                bond(1L, "2030-01-01"),
                bond(2L, "2025-05-05"),
                bond(3L, null),
                bond(4L, "2026-12-31"),
                bond(5L, "2025-05-05"))));

        List<Bond> result = bondService.getAllBonds();

        assertEquals(List.of(2L, 5L, 4L, 1L, 3L), ids(result),
                "maturity date ascending, same-date tie broken by id, nulls last");
    }

    @Test
    void regularUserGetsSameDefaultOrdering() {
        Client me = new Client();
        me.setId(9L);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireClient()).thenReturn(me);
        when(bondRepository.findByClientId(anyLong())).thenReturn(new ArrayList<>(List.of(
                bond(11L, "2028-07-01"),
                bond(12L, "2024-02-29"),
                bond(13L, "2027-11-15"))));

        List<Bond> result = bondService.getAllBonds();

        assertEquals(List.of(12L, 13L, 11L), ids(result));
        assertEquals(LocalDate.parse("2024-02-29"), result.get(0).getMaturityDate(),
                "earliest maturity first");
    }
}
