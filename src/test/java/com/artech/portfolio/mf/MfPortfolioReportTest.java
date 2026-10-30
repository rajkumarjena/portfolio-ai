package com.artech.portfolio.mf;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.mf.controller.MfPortfolioReportController;
import com.artech.portfolio.mf.entity.MfAmc;
import com.artech.portfolio.mf.entity.MfFolio;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import com.artech.portfolio.mf.repository.MfFolioRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeValuationRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import com.artech.portfolio.mf.service.MfPortfolioReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/mf-portfolio-report.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.mail.host=localhost", "spring.mail.username=test", "spring.mail.password=test"
})
class MfPortfolioReportTest {
    private static final String OWNER = "MFREPORTTEST01";
    private static final String OTHER = "MFREPORTOTHER01";
    @Autowired private MfPortfolioReportController report;
    @Autowired private ClientRepository clients;
    @Autowired private MfFolioRepository folios;
    @Autowired private MfFolioSchemeRepository schemes;
    @Autowired private MfFolioSchemeTransactionRepository transactions;
    @Autowired private MfFolioSchemeValuationRepository valuations;
    @Autowired private MfPortfolioSummaryRepository summaries;
    @Autowired private com.artech.portfolio.mf.repository.MfAmcRepository amcs;

    @BeforeEach
    void clearData() {
        SecurityContextHolder.clearContext();
        transactions.deleteByOwner(OWNER);
        transactions.deleteByOwner(OTHER);
        valuations.deleteByOwner(OWNER);
        valuations.deleteByOwner(OTHER);
        schemes.deleteByOwner(OWNER);
        schemes.deleteByOwner(OTHER);
        folios.deleteByOwner(OWNER);
        folios.deleteByOwner(OTHER);
        summaries.deleteByOwner(OWNER);
        summaries.deleteByOwner(OTHER);
        clients.findByPanIgnoreCase(OWNER).forEach(clients::delete);
        clients.findByPanIgnoreCase(OTHER).forEach(clients::delete);
        amcs.findAll().stream()
                .filter(amc -> amc.getNormalizedName().startsWith("REPORT TEST AMC"))
                .forEach(amcs::delete);
        saveClient(OWNER);
        saveClient(OTHER);
    }

    @Test
    void listsAllAmcsAndTotalsAndShowsOnlySelectedOwnersDetails() {
        Long amcId = seedAmc();
        addSummary(OWNER, amcId, "100.00", "135.00", false);
        addSummary(OWNER, null, "100.00", "135.00", true);
        addSummary(OTHER, amcId, "900.00", "999.00", false);

        MfFolioScheme scheme = seedScheme(OWNER, amcId, "F-1", "Test Scheme", "INF000TEST01");
        saveValuation(OWNER, scheme.getId(), "2026-10-01", "100", "135", "13.5");
        saveTxn(OWNER, scheme.getId(), "2026-09-02", "PURCHASE", "Test purchase", "10", "100", "13.5");
        seedFolio(OTHER, amcId, "OTHER-F");

        authenticate(OWNER);
        Map<String, Object> summary = report.summary(null, null, null);
        List<?> rows = (List<?>) summary.get("rows");
        assertEquals(1, rows.size());
        @SuppressWarnings("unchecked") Map<String, Object> summaryRow = (Map<String, Object>) rows.get(0);
        assertEquals("Report Test AMC", summaryRow.get("amc"));
        @SuppressWarnings("unchecked") Map<String, Object> total = (Map<String, Object>) summary.get("total");
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) total.get("costValue")));
        assertEquals(0, new BigDecimal("135.00").compareTo((BigDecimal) total.get("marketValue")));

        Map<String, Object> detail = report.detail(amcId, null, null, null, 1, 50);
        assertEquals("Report Test AMC", detail.get("amc"));
        assertEquals(1, ((List<?>) detail.get("schemes")).size());
        assertEquals(1, ((List<?>) detail.get("transactions")).size());
        assertEquals(1, ((Map<?, ?>) detail.get("pagination")).get("total"));

        authenticate(OTHER);
        Map<String, Object> otherSummary = report.summary(null, null, null);
        assertEquals(1, ((List<?>) otherSummary.get("rows")).size(), "each user only sees their own AMC summary");
        assertEquals(0, ((List<?>) report.detail(amcId, null, null, null, 1, 50).get("schemes")).size(),
                "AMC detail must not leak another client's schemes");
        assertEquals(0, ((Map<?, ?>) report.transactions(null, null, null, null, null, 1, 50)
                .get("totals")).get("count"), "the ledger of another client is never returned");
    }

    /** MFR_V9 5: the range numbers come from the ledger, not the snapshot. */
    @Test
    void derivesCostAndMarketValueOfADateRangeFromTheLedger() {
        Long amcId = seedAmc();
        addSummary(OWNER, amcId, "100.00", "135.00", false);
        addSummary(OWNER, null, "100.00", "135.00", true);
        MfFolioScheme scheme = seedScheme(OWNER, amcId, "R-1", "Range Scheme", "INF000RANGE1");
        saveValuation(OWNER, scheme.getId(), "2026-10-01", "100", "135", "13.5");
        saveTxn(OWNER, scheme.getId(), "2026-09-02", "PURCHASE", "Inside the range", "10", "100", "13.5");
        saveTxn(OWNER, scheme.getId(), "2026-09-15", "PURCHASE", "Inside the range", "5", "200", "14");
        saveTxn(OWNER, scheme.getId(), "2025-01-01", "PURCHASE", "Before the range", "20", "500", "10");

        authenticate(OWNER);
        Map<String, Object> ranged = report.summary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        assertEquals(Boolean.TRUE, ranged.get("derived"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) ranged.get("rows");
        assertEquals(1, rows.size());
        // cost = 100 + 200, market = (10 + 5) units x the last NAV of the range (14)
        assertEquals(0, new BigDecimal("300").compareTo((BigDecimal) rows.get(0).get("costValue")));
        assertEquals(0, new BigDecimal("210").compareTo((BigDecimal) rows.get(0).get("marketValue")));
        assertEquals(2L, rows.get(0).get("txnCount"));
        @SuppressWarnings("unchecked") Map<String, Object> total = (Map<String, Object>) ranged.get("total");
        assertEquals(0, new BigDecimal("300").compareTo((BigDecimal) total.get("costValue")));
        assertEquals(0, new BigDecimal("210").compareTo((BigDecimal) total.get("marketValue")));

        Map<String, Object> emptyRange = report.summary(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null);
        assertEquals(0, ((List<?>) emptyRange.get("rows")).size(), "a range without movements has no AMC row");
    }

    /** MFR_V9 3/4/7: the AMC and scheme filters narrow every report. */
    @Test
    void filtersTheAmcSummaryAndTheDetailByAmcAndScheme() {
        Long amcId = seedAmc();
        Long otherAmc = seedAmc("Report Test AMC Two", "REPORT TEST AMC TWO");
        addSummary(OWNER, amcId, "100.00", "135.00", false);
        addSummary(OWNER, otherAmc, "50.00", "60.00", false);
        MfFolioScheme first = seedScheme(OWNER, amcId, "S-1", "Alpha Scheme", "INF000ALPHA1");
        MfFolioScheme second = seedScheme(OWNER, amcId, "S-2", "Beta Scheme", "INF000BETA01");
        saveTxn(OWNER, first.getId(), "2026-09-02", "PURCHASE", "Alpha buy", "10", "100", "13.5");
        saveTxn(OWNER, second.getId(), "2026-09-03", "PURCHASE", "Beta buy", "4", "40", "10");

        authenticate(OWNER);
        Map<String, Object> filtered = report.summary(null, null, otherAmc);
        assertEquals(1, ((List<?>) filtered.get("rows")).size());
        assertEquals("Report Test AMC Two", ((Map<?, ?>) ((List<?>) filtered.get("rows")).get(0)).get("amc"));

        Map<String, Object> detail = report.detail(amcId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                "Beta", 1, 50);
        assertEquals(1, ((List<?>) detail.get("schemes")).size());
        assertEquals(1, ((List<?>) detail.get("transactions")).size());
        assertEquals(0, new BigDecimal("40").compareTo((BigDecimal) detail.get("costValue")));
        assertEquals(0, new BigDecimal("40").compareTo((BigDecimal) detail.get("marketValue")));

        assertThrows(ResponseStatusException.class,
                () -> report.detail(999_999L, null, null, null, 1, 50), "an unknown AMC is a 404");
    }

    /** MFR_V9 1/2/6: the ledger is tabular, paged 50 rows at a time. */
    @Test
    void pagesTheAllTransactionsReportInFiftyRowWindows() {
        Long amcId = seedAmc();
        MfFolioScheme scheme = seedScheme(OWNER, amcId, "P-1", "Paged Scheme", "INF000PAGED1");
        for (int index = 0; index < 120; index++) {
            saveTxn(OWNER, scheme.getId(), "2026-09-01", "PURCHASE", "Row " + index, "1", "100", "13.5");
        }
        seedScheme(OTHER, amcId, "P-2", "Other Owner Scheme", "INF000OTHER1");

        authenticate(OWNER);
        Map<String, Object> first = report.transactions(null, null, null, null, null, 1, 0);
        assertEquals(MfPortfolioReportService.DEFAULT_PAGE_SIZE, ((List<?>) first.get("rows")).size());
        Map<?, ?> pager = (Map<?, ?>) first.get("pagination");
        assertEquals(120, pager.get("total"));
        assertEquals(Boolean.TRUE, pager.get("hasMore"));
        Map<?, ?> totals = (Map<?, ?>) first.get("totals");
        assertEquals(120, totals.get("count"));
        assertEquals(0, new BigDecimal("12000").compareTo((BigDecimal) totals.get("amount")));
        assertEquals(0, new BigDecimal("1620").compareTo((BigDecimal) totals.get("marketValue")));

        Map<String, Object> last = report.transactions(null, null, null, null, null, 3, 50);
        assertEquals(20, ((List<?>) last.get("rows")).size());
        assertFalse((Boolean) ((Map<?, ?>) last.get("pagination")).get("hasMore"));

        Map<String, Object> searched = report.transactions(null, null, null, null, "Row 7", 1, 50);
        assertEquals(11, ((Map<?, ?>) searched.get("totals")).get("count"), "Row 7 matches 7, 70-79");
        assertTrue(((List<?>) searched.get("schemeNames")).contains("Paged Scheme"),
                "the scheme filter offers the owner's schemes");
    }

    /**
     * MFR_V10 2/3/4: a stamp duty line is never shown - its amount is added to
     * the purchase of the same date and scheme, before counting and paging.
     */
    @Test
    void foldsStampDutyIntoThePurchaseOfTheSameDayAndScheme() {
        Long amcId = seedAmc();
        MfFolioScheme sipScheme = seedScheme(OWNER, amcId, "T-1", "Stamp Duty SIP Scheme", "INF000STAMP1");
        MfFolioScheme otherScheme = seedScheme(OWNER, amcId, "T-2", "Stamp Duty Other Scheme", "INF000STAMP2");
        saveTxn(OWNER, sipScheme.getId(), "2026-10-07", "PURCHASE_SIP", "Systematic Investment (3/13)",
                "14.504", "4499.78", "310.25");
        saveStampDuty(OWNER, sipScheme.getId(), "2026-10-07", "0.22");
        saveTxn(OWNER, otherScheme.getId(), "2026-10-07", "PURCHASE_SIP", "Systematic Investment (1/6)",
                "5", "1000", "200");
        saveStampDuty(OWNER, otherScheme.getId(), "2026-10-07", "0.5");
        saveTxn(OWNER, otherScheme.getId(), "2026-09-01", "PURCHASE", "Lumpsum purchase", "9", "999.5", "111");
        saveStampDuty(OWNER, otherScheme.getId(), "2026-09-01", "0.5");

        authenticate(OWNER);
        Map<String, Object> report1 = report.transactions(null, null, null, null, null, 1, 50);
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows =
                (List<Map<String, Object>>) report1.get("rows");
        assertEquals(3, rows.size(), "the three stamp duty rows are folded away");
        assertTrue(rows.stream().noneMatch(row -> "STAMP_DUTY_TAX".equals(row.get("type"))),
                "no stamp duty row is ever returned");
        assertEquals(0, new BigDecimal("4500.00").compareTo(amountOf(rows, "Systematic Investment (3/13)")));
        assertEquals(0, new BigDecimal("1000.50").compareTo(amountOf(rows, "Systematic Investment (1/6)")));
        assertEquals(0, new BigDecimal("1000.00").compareTo(amountOf(rows, "Lumpsum purchase")));
        assertEquals(3, ((Map<?, ?>) report1.get("totals")).get("count"));
        assertEquals(0, new BigDecimal("6500.50").compareTo(
                (BigDecimal) ((Map<?, ?>) report1.get("totals")).get("amount")));

        // the AMC detail page folds exactly the same way
        Map<String, Object> detail = report.detail(amcId, null, null, null, 1, 50);
        @SuppressWarnings("unchecked") List<Map<String, Object>> detailRows =
                (List<Map<String, Object>>) detail.get("transactions");
        assertEquals(3, detailRows.size());
        assertEquals(3, detail.get("txnCount"));
        assertTrue(detailRows.stream().noneMatch(row -> "STAMP_DUTY_TAX".equals(row.get("type"))));
    }

    // ------------------------------------------------------------- helpers --
    private Long seedAmc() {
        return seedAmc("Report Test AMC", "REPORT TEST AMC");
    }

    private Long seedAmc(String name, String normalized) {
        MfAmc amc = new MfAmc();
        amc.setName(name);
        amc.setNormalizedName(normalized);
        return amcs.save(amc).getId();
    }

    private MfFolio seedFolio(String owner, Long amcId, String folioNo) {
        MfFolio folio = new MfFolio();
        folio.setOwnerPan(owner);
        folio.setFolioNo(folioNo);
        folio.setAmcId(amcId);
        return folios.save(folio);
    }

    private MfFolioScheme seedScheme(String owner, Long amcId, String folioNo, String name, String isin) {
        MfFolio savedFolio = seedFolio(owner, amcId, folioNo);
        MfFolioScheme scheme = new MfFolioScheme();
        scheme.setOwnerPan(owner);
        scheme.setFolioId(savedFolio.getId());
        scheme.setScheme(name);
        scheme.setIsin(isin);
        scheme.setCloseUnits(BigDecimal.TEN);
        return schemes.save(scheme);
    }

    private void saveValuation(String owner, Long schemeId, String date, String cost, String market, String nav) {
        MfFolioSchemeValuation valuation = new MfFolioSchemeValuation();
        valuation.setOwnerPan(owner);
        valuation.setSchemeId(schemeId);
        valuation.setValueDate(LocalDate.parse(date));
        valuation.setCostValue(new BigDecimal(cost));
        valuation.setMarketValue(new BigDecimal(market));
        valuation.setNav(new BigDecimal(nav));
        valuations.save(valuation);
    }

    private void saveTxn(String owner, Long schemeId, String date, String type, String description,
            String units, String amount, String nav) {
        MfFolioSchemeTransaction txn = new MfFolioSchemeTransaction();
        txn.setOwnerPan(owner);
        txn.setSchemeId(schemeId);
        txn.setTxnDate(LocalDate.parse(date));
        txn.setType(type);
        txn.setDescription(description);
        txn.setUnits(new BigDecimal(units));
        txn.setAmount(new BigDecimal(amount));
        txn.setNav(new BigDecimal(nav));
        transactions.save(txn);
    }

    private void saveStampDuty(String owner, Long schemeId, String date, String amount) {
        MfFolioSchemeTransaction txn = new MfFolioSchemeTransaction();
        txn.setOwnerPan(owner);
        txn.setSchemeId(schemeId);
        txn.setTxnDate(LocalDate.parse(date));
        txn.setType("STAMP_DUTY_TAX");
        txn.setDescription("*** Stamp Duty ***");
        txn.setAmount(new BigDecimal(amount));
        transactions.save(txn);
    }

    private static BigDecimal amountOf(List<Map<String, Object>> rows, String description) {
        return rows.stream().filter(row -> description.equals(row.get("description")))
                .map(row -> (BigDecimal) row.get("amount"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + description));
    }

    private void saveClient(String pan) {
        Client client = new Client(); client.setPan(pan); client.setName(pan); clients.save(client);
    }

    private static void authenticate(String pan) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                pan, "", AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private void addSummary(String owner, Long amcId, String cost, String market, boolean total) {
        MfPortfolioSummary row = new MfPortfolioSummary(); row.setOwnerPan(owner); row.setAmcId(amcId);
        row.setCostValue(new BigDecimal(cost)); row.setMarketValue(new BigDecimal(market)); row.setTotal(total);
        summaries.save(row);
    }
}
