package com.artech.portfolio.mf;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientEmail;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.mf.entity.MfAmc;
import com.artech.portfolio.mf.entity.MfFolio;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.entity.MfSummaryReconciliationLine;
import com.artech.portfolio.mf.parser.MfSummaryParser;
import com.artech.portfolio.mf.parser.MfSummaryReport;
import com.artech.portfolio.mf.repository.MfAmcRepository;
import com.artech.portfolio.mf.repository.MfFolioRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mf.repository.MfImportBatchRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import com.artech.portfolio.mf.repository.MfSummaryReconciliationRepository;
import com.artech.portfolio.mf.service.MfSummaryImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/mf-summary-flow.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.mail.host=localhost",
        "spring.mail.username=test",
        "spring.mail.password=test"
})
class MfSummaryImportFlowTest {
    private static final String PAN = "SUMMARYTEST01";
    private static final String EMAIL = "rajkumarjena@gmail.com";
    private static final Path PDF = Path.of("consolidated_account_summary.pdf");
    private static final String PASSWORD = "Anmol$09";

    @Autowired private MfSummaryImportService service;
    @Autowired private ClientRepository clients;
    @Autowired private MfImportBatchRepository batches;
    @Autowired private MfSummaryReconciliationRepository reviewRows;
    @Autowired private MfFolioRepository folios;
    @Autowired private MfFolioSchemeRepository schemes;
    @Autowired private MfPortfolioSummaryRepository summaries;
    @Autowired private MfAmcRepository amcs;
    @Autowired private com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository transactions;

    @BeforeEach
    void resetFixture() {
        reviewRows.deleteByOwner(PAN);
        transactions.deleteByOwner(PAN);
        batches.deleteAll(batches.findByPanOrderByUploadedAtDesc(PAN));
        summaries.deleteByOwner(PAN);
        schemes.deleteByOwner(PAN);
        folios.deleteByOwner(PAN);
        clients.findByPanIgnoreCase(PAN).forEach(clients::delete);
        Client client = new Client();
        client.setPan(PAN);
        client.setName("Summary Test Client");
        ClientEmail email = new ClientEmail();
        email.setEmail(EMAIL);
        client.addEmail(email);
        clients.save(client);
    }

    @Test
    @Transactional
    void rejectsUnregisteredSummaryEmailBeforeStaging() throws Exception {
        byte[] bytes = Files.readAllBytes(PDF);
        Client client = clients.findByPanIgnoreCase(PAN).get(0);
        client.getEmails().get(0).setEmail("different@example.com");
        clients.save(client);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upload(PAN, PDF.getFileName().toString(), bytes, PASSWORD));
        assertTrue(error.getMessage().contains("Email Id"));
        assertTrue(batches.findByPanOrderByUploadedAtDesc(PAN).isEmpty());
        assertTrue(reviewRows.findByBatchIdAndOwnerPanOrderById(-1L, PAN).isEmpty());
    }

    @Test
    void detectsUniquePartialFolioMatchAndHighlightsItForReview() throws Exception {
        MfSummaryReport parsed = new MfSummaryParser().parse(Files.readAllBytes(PDF), PASSWORD);
        MfSummaryReport.Line line = parsed.lines.get(0);
        MfAmc amc = new MfAmc();
        amc.setName("Probable Match Fund");
        amc.setNormalizedName("PROBABLE MATCH FUND");
        amc = amcs.save(amc);
        MfFolio folio = new MfFolio();
        folio.setOwnerPan(PAN);
        folio.setFolioNo(line.folio + "/ 40");
        folio.setAmc(amc.getName());
        folio.setAmcId(amc.getId());
        folio.setRefreshFrom("DETAIL_STATEMENT");
        folio = folios.save(folio);
        MfFolioScheme priorScheme = new MfFolioScheme();
        priorScheme.setOwnerPan(PAN);
        priorScheme.setFolioId(folio.getId());
        priorScheme.setScheme(line.scheme);
        priorScheme.setIsin(line.isin);
        priorScheme.setRefreshFrom("DETAIL_STATEMENT");
        schemes.save(priorScheme);

        Map<String, Object> uploaded = service.upload(PAN, PDF.getFileName().toString(), Files.readAllBytes(PDF), PASSWORD);
        MfImportBatch batch = (MfImportBatch) uploaded.get("batch");
        @SuppressWarnings("unchecked")
        java.util.List<MfSummaryReconciliationLine> rows =
                (java.util.List<MfSummaryReconciliationLine>) service.report(batch.getId(), PAN).get("rows");
        MfSummaryReconciliationLine matched = rows.stream().filter(row -> row.getFolioNo().equals(line.folio))
                .findFirst().orElseThrow();
        assertEquals(folio.getId(), matched.getResolvedFolioId());
        assertEquals(amc.getId(), matched.getAmcId());
        assertTrue(matched.isProbableFolioMatch());
        assertEquals("AMBER", matched.getMatchStatus());
        assertTrue(matched.getMessage().contains(folio.getFolioNo()));

        Map<Long, Map<String, String>> corrections = new LinkedHashMap<>();
        rows.forEach(row -> corrections.put(row.getId(), Map.of(
                "amc", "Corrected Probable Match Fund", "scheme", row.getScheme())));
        service.accept(batch.getId(), PAN, corrections);
        MfAmc correctedAmc = amcs.findByNormalizedName("CORRECTED PROBABLE MATCH FUND").get(0);
        MfFolio refreshedFolio = folios.findById(folio.getId()).orElseThrow();
        assertEquals(correctedAmc.getId(), refreshedFolio.getAmcId(),
                "accepting a corrected AMC relinks the matched folio");
        assertTrue(schemes.findByFolioId(folio.getId()).stream()
                .allMatch(scheme -> correctedAmc.getId().equals(scheme.getFolioReference().getAmcId())),
                "existing schemes resolve AMC through the corrected parent folio");
    }

    @Test
    void uploadsReviewsAndAcceptsSummaryWithUserResolvedAmc() throws Exception {
        Map<String, Object> uploaded = service.upload(PAN, PDF.getFileName().toString(), Files.readAllBytes(PDF), PASSWORD);
        MfImportBatch batch = (MfImportBatch) uploaded.get("batch");
        assertEquals("SUMMARY", batch.getStatementKind());
        assertEquals(27, batch.getReportTo().getDayOfMonth());

        Map<String, Object> report = service.report(batch.getId(), PAN);
        assertEquals("AMBER", report.get("status"), "unknown AMC labels require user review");
        assertEquals(0, new java.math.BigDecimal("3294237.30").compareTo((java.math.BigDecimal) report.get("expectedCost")));
        assertEquals(0, new java.math.BigDecimal("5314530.97").compareTo((java.math.BigDecimal) report.get("expectedMarket")));

        @SuppressWarnings("unchecked")
        java.util.List<com.artech.portfolio.mf.entity.MfSummaryReconciliationLine> rows =
                (java.util.List<com.artech.portfolio.mf.entity.MfSummaryReconciliationLine>) report.get("rows");
        assertFalse(rows.isEmpty());
        Map<Long, Map<String, String>> corrections = new LinkedHashMap<>();
        rows.forEach(row -> corrections.put(row.getId(), Map.of("amc", "Unit Test Mutual Fund", "scheme", row.getScheme())));
        Map<String, Object> accepted = service.accept(batch.getId(), PAN, corrections);
        assertEquals("GREEN", accepted.get("status"));
        assertEquals("ACCEPTED", batches.findById(batch.getId()).orElseThrow().getStatus());
        assertEquals(rows.size(), schemes.findByOwnerPan(PAN).size());
        assertEquals(1, amcs.findByNormalizedName("UNIT TEST MUTUAL FUND").size());
        assertTrue(folios.findByOwnerPanOrderByFolioNo(PAN).stream().allMatch(folio ->
                folio.getAmcId() != null && "SUMMARY_STATEMENT".equals(folio.getRefreshFrom())));
        assertTrue(schemes.findByOwnerPan(PAN).stream().allMatch(scheme ->
                scheme.getFolioReference() != null && scheme.getFolioReference().getAmcId() != null
                        && "SUMMARY_STATEMENT".equals(scheme.getRefreshFrom())));
        assertTrue(summaries.findByOwnerPanOrderByAmc(PAN).stream().filter(row -> !row.isTotal())
                .allMatch(row -> row.getAmcId() != null
                        && "SUMMARY_STATEMENT".equals(row.getMatchCondition())
                        && "SUMMARY_STATEMENT".equals(row.getRefreshFrom())));
        assertTrue(summaries.findByOwnerPanOrderByAmc(PAN).stream().anyMatch(row -> row.isTotal()
                && row.getMatchCondition().equals("SUMMARY_STATEMENT")));
    }
}
