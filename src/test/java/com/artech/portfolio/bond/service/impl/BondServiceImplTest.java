package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.dto.BondPageView;
import com.artech.portfolio.bond.dto.BondRowView;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.util.BondConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.util.ClientConstants;
import com.artech.portfolio.common.controller.AppParamController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BONDBP_V9 bond lifecycle rules:
 *   #2/#6 a direct entry is always One Time Investment + EXECUTED, and a SIP
 *         instalment row arrives In Progress / SIP Investment,
 *   #4.h  completing every mandatory detail on the Bond Details Entry screen
 *         flips an In Progress record to EXECUTED (server-decided),
 *   #7/#11 lifecycle fields never come from a request body,
 *   #9    the section listing splits active/matured, puts In Progress first
 *         and paginates server-side,
 *   #10   a matured record can be viewed only - no edit, no delete.
 */
class BondServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private BondRepository bondRepository;
    private CurrentUserService currentUserService;
    private AppParamController appParamController;
    private BondServiceImpl bondService;

    private Client owner;
    private Bond storedBond;
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        bondRepository = mock(BondRepository.class);
        currentUserService = mock(CurrentUserService.class);
        appParamController = mock(AppParamController.class);
        when(appParamController.getConfig()).thenReturn(List.of());

        owner = new Client();
        owner.setId(9L);
        owner.setPan("OWNER0001A");
        owner.setName("Owner");
        when(currentUserService.requireClient()).thenReturn(owner);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(currentUserService.hasAnyRole(any(), any())).thenReturn(true);

        when(bondRepository.save(any(Bond.class))).thenAnswer(inv -> {
            Bond bond = inv.getArgument(0);
            if (bond.getId() == null) {
                bond.setId(nextId++);
            }
            storedBond = bond;
            return bond;
        });
        when(bondRepository.findById(any())).thenAnswer(inv ->
                Objects.equals(storedBond != null ? storedBond.getId() : null, inv.getArgument(0))
                        ? java.util.Optional.of(storedBond) : java.util.Optional.empty());

        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        bondService = new BondServiceImpl(bondRepository, currentUserService,
                appParamController, clock);
    }

    // ------------------------------------------------------------- fixtures

    /** The mapped subset a Bond SIP instalment row arrives with (#4.b-#4.f). */
    private Bond incompleteSipBond() {
        Bond bond = new Bond();
        bond.setIssuer("1");
        bond.setInvestmentDate(TODAY);
        bond.setInvestmentAmountPerUnit(5000);
        bond.setInterestPaymentStartDate(TODAY);
        bond.setCouponFrequency("11");
        bond.setStatus(BondConstants.STATUS_IN_PROGRESS);
        bond.setInvestmentType(BondConstants.INVESTMENT_TYPE_SIP);
        bond.setBondSipId(7L);
        return bond;
    }

    /** What the Bond Details Entry screen submits once every mandatory field is filled. */
    private Bond completedDetails() {
        Bond details = new Bond();
        details.setBondName("Alpha NCD 2030");
        details.setIssuer("1");
        details.setCreditRating("5");
        details.setInvestmentDate(TODAY);
        details.setInvestmentAmountPerUnit(5000);
        details.setFaceValuePerUnit(1000);
        details.setUnits(5);
        details.setCouponRate(8.5);
        details.setCouponType("Fixed Interest");
        details.setCouponFrequency("11");
        details.setInterestPaymentStartDate(TODAY);
        details.setMaturityDate(LocalDate.of(2030, 1, 1));
        details.setTypeOfSecurity("Secured - Regular Bond/Debenture");
        details.setRepaymentPriority("Senior");
        details.setStaggeredBond("No");
        details.setListing("Listed");
        details.setNotes("completed");
        return details;
    }

    private Bond maturedBond() {
        Bond bond = completedDetails();
        bond.setId(nextId++);
        bond.setStatus(BondConstants.STATUS_EXECUTED);
        bond.setInvestmentType(BondConstants.INVESTMENT_TYPE_ONE_TIME);
        bond.setMaturityDate(LocalDate.of(2020, 1, 1));
        storedBond = bond;
        return bond;
    }

    private ResponseStatusException expectRejected(long id, Bond details) {
        return assertThrows(ResponseStatusException.class, () -> bondService.updateBond(id, details));
    }

    // --------------------------------------------------------------- #6 create

    @Test
    void directEntryIsAlwaysOneTimeInvestmentAndExecuted() {
        Bond payload = completedDetails();
        payload.setStatus(BondConstants.STATUS_IN_PROGRESS);          // body never wins
        payload.setInvestmentType(BondConstants.INVESTMENT_TYPE_SIP);
        payload.setBondSipId(77L);

        Bond saved = bondService.saveBond(payload);

        assertEquals(BondConstants.INVESTMENT_TYPE_ONE_TIME, saved.getInvestmentType(), "#6");
        assertEquals(BondConstants.STATUS_EXECUTED, saved.getStatus(), "#6");
        assertNull(saved.getBondSipId(), "a direct entry is not a SIP row");
        assertEquals(owner.getId(), saved.getClient().getId(), "session owns the row");
        assertNotNull(saved.getId(), "persisted with a generated id");
    }

    // --------------------------------------------------------------- #4.h update

    @Test
    void incompleteSipRecordStaysInProgress() {
        Bond stored = incompleteSipBond();
        stored.setId(nextId++);
        storedBond = stored;

        Bond incomplete = completedDetails();
        incomplete.setMaturityDate(null);        // still missing one mandatory detail

        Bond result = bondService.updateBond(stored.getId(), incomplete);

        assertEquals(BondConstants.STATUS_IN_PROGRESS, result.getStatus());
    }

    @Test
    void completingMandatoryDetailsTurnsSipRecordExecuted() {
        Bond stored = incompleteSipBond();
        stored.setId(nextId++);
        storedBond = stored;

        Bond result = bondService.updateBond(stored.getId(), completedDetails());

        assertEquals(BondConstants.STATUS_EXECUTED, result.getStatus(), "#4.h");
        assertEquals(BondConstants.INVESTMENT_TYPE_SIP, result.getInvestmentType(), "#7 type is fixed");
        assertEquals(7L, result.getBondSipId(), "SIP link untouched");
    }

    @Test
    void lifecycleFieldsCannotBeForcedByTheRequestBody() {
        Bond stored = incompleteSipBond();
        stored.setId(nextId++);
        storedBond = stored;

        Bond hostile = completedDetails();
        hostile.setMaturityDate(null);           // keep it incomplete
        hostile.setStatus(BondConstants.STATUS_EXECUTED);
        hostile.setInvestmentType(BondConstants.INVESTMENT_TYPE_ONE_TIME);
        hostile.setBondSipId(null);

        Bond result = bondService.updateBond(stored.getId(), hostile);

        assertEquals(BondConstants.STATUS_IN_PROGRESS, result.getStatus(), "#11 body never sets status");
        assertEquals(BondConstants.INVESTMENT_TYPE_SIP, result.getInvestmentType(), "#11 body never sets type");
        assertEquals(7L, result.getBondSipId());
    }

    @Test
    void plainUserCannotEditBonds() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.hasAnyRole(any(), any())).thenReturn(false);

        ResponseStatusException error = expectRejected(1L, completedDetails());

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    // --------------------------------------------------------------- #10 matured

    @Test
    void maturedBondCannotBeEdited() {
        Bond matured = maturedBond();

        ResponseStatusException error = expectRejected(matured.getId(), completedDetails());

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("viewed"), error.getReason());
    }

    @Test
    void maturedBondCannotBeDeleted() {
        Bond matured = maturedBond();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> bondService.deleteBond(matured.getId()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("viewed"), error.getReason());
    }

    // ------------------------------------------------------------- #9 sections

    private Bond listBond(long id, String status, String maturity, String investmentDate) {
        Bond bond = completedDetails();
        bond.setId(id);
        bond.setStatus(status);
        // Real rows pair In Progress with the SIP origin (#4.b) and EXECUTED
        // with a direct entry (#6); the migration backfills the same way.
        bond.setInvestmentType(BondConstants.STATUS_IN_PROGRESS.equals(status)
                ? BondConstants.INVESTMENT_TYPE_SIP : BondConstants.INVESTMENT_TYPE_ONE_TIME);
        bond.setMaturityDate(maturity == null ? null : LocalDate.parse(maturity));
        bond.setInvestmentDate(LocalDate.parse(investmentDate));
        return bond;
    }

    @Test
    void sectionSplitsOrdersAndPaginatesServerSide() {
        when(bondRepository.findAll()).thenReturn(List.of(
                listBond(1L, BondConstants.STATUS_EXECUTED, "2030-01-01", "2025-01-01"),
                listBond(2L, BondConstants.STATUS_EXECUTED, "2020-05-05", "2024-01-01"),
                listBond(3L, BondConstants.STATUS_IN_PROGRESS, null, "2026-01-01"),
                listBond(4L, BondConstants.STATUS_EXECUTED, "2028-01-01", "2023-01-01"),
                listBond(5L, BondConstants.STATUS_EXECUTED, "2019-01-01", "2022-01-01")));

        BondPageView active = bondService.listSection(BondConstants.SECTION_ACTIVE, 0, 20, null);
        assertEquals(3, active.getTotalElements());
        assertEquals(3L, active.getContent().get(0).getId(), "#9 In Progress on top");
        assertEquals(List.of(3L, 4L, 1L),
                active.getContent().stream().map(BondRowView::getId).toList(),
                "then Investment Date ascending (nulls last)");
        assertEquals(BondConstants.STATUS_IN_PROGRESS, active.getContent().get(0).getStatus());
        assertEquals(BondConstants.INVESTMENT_TYPE_SIP, active.getContent().get(0).getInvestmentType());

        BondPageView expired = bondService.listSection(BondConstants.SECTION_EXPIRED, 0, 20, null);
        assertEquals(2, expired.getTotalElements());
        assertEquals(List.of(5L, 2L),
                expired.getContent().stream().map(BondRowView::getId).toList(),
                "matured history, oldest investment date first");
    }

    @Test
    void sectionPaginationSlicesServerSide() {
        when(bondRepository.findAll()).thenReturn(List.of(
                listBond(1L, BondConstants.STATUS_IN_PROGRESS, "2031-01-01", "2026-10-20"),
                listBond(2L, BondConstants.STATUS_EXECUTED, "2032-01-01", "2026-10-20"),
                listBond(3L, BondConstants.STATUS_EXECUTED, "2033-01-01", "2026-10-20")));

        BondPageView page1 = bondService.listSection(BondConstants.SECTION_ACTIVE, 0, 2, null);
        assertEquals(3, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());
        assertEquals(List.of(1L, 2L), page1.getContent().stream().map(BondRowView::getId).toList());

        BondPageView page2 = bondService.listSection(BondConstants.SECTION_ACTIVE, 1, 2, null);
        assertEquals(List.of(3L), page2.getContent().stream().map(BondRowView::getId).toList());
        assertEquals(1, page2.getPage());
    }

    @Test
    void nonAdminSectionIgnoresForeignClientFilter() {
        Client me = new Client();
        me.setId(9L);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.requireClient()).thenReturn(me);
        when(bondRepository.findByClientId(anyLong())).thenReturn(List.of(
                listBond(11L, BondConstants.STATUS_EXECUTED, "2030-01-01", "2026-01-01")));

        BondPageView page = bondService.listSection(BondConstants.SECTION_ACTIVE, 0, 20, 777L);

        assertEquals(1, page.getTotalElements());
        assertEquals(11L, page.getContent().get(0).getId(), "scoped to the signed-in client, filter ignored");
        org.mockito.Mockito.verify(bondRepository).findByClientId(9L);
        org.mockito.Mockito.verify(bondRepository, org.mockito.Mockito.never()).findByClientId(777L);
    }
}
