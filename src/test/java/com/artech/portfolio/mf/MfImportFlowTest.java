package com.artech.portfolio.mf;

import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.parser.KfCasParser;
import com.artech.portfolio.mf.repository.MfAmcRepository;
import com.artech.portfolio.mf.repository.MfFolioRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeNomineeRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeValuationRepository;
import com.artech.portfolio.mf.repository.MfImportBatchRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpFolioRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpNomineeRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpPortfolioSummaryRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpSchemeRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpTransactionRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpValuationRepository;
import com.artech.portfolio.mf.service.MfImportService;
import com.artech.portfolio.mf.service.MfReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end check of the KFINTECH upload of MF.md 1-3 / 9 / 10 / 12 / 13
 * against the statement supplied with the specification: read the report, stage
 * it, book the ADJUSTED rows of a first import, reconcile it against the
 * accepted data and accept it.
 *
 * <p>The datasource is a throw away sqlite file under target/, so neither the
 * developer's database nor another test is touched.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/mf-import-flow.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        // the context must start without a mail server (see application.yaml)
        "spring.mail.host=localhost",
        "spring.mail.username=test",
        "spring.mail.password=test"
})
class MfImportFlowTest {

    private static final Path REPORT = Path.of("102735162510202607363428594863322076.pdf");
    /** MF_V3 2 - the statement whose period the subset below sits inside. */
    private static final Path SUPERSET = Path.of("consolidated_account_stmt_01-09-01-25-10-26.pdf");
    /** MF_V3 2 - 14-May-2025 to 23-Oct-2025, entirely inside the superset. */
    private static final Path SUBSET = Path.of("consolidated_account_stmt_14-05-25-23-10-25.pdf");
    private static final String PASSWORD = "Anmol$09";

    /**
     * MF_V3 4 - a statement may only be uploaded by the client whose PAN it
     * carries, so the owner of this test is the PAN printed on the sample
     * statement. The datasource is a throw away sqlite file under target/ and
     * is recreated for every run, so no earlier state can leak into the first
     * import each test asserts on.
     */
    private static final String OWNER = "AIGPR6090R";

    static {
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            try {
                Files.deleteIfExists(Path.of("target/mf-import-flow.db" + suffix));
            } catch (Exception ignored) {
                // a leftover file of an earlier run that cannot be removed is
                // cleared per test instead (see clearOwnersData)
            }
        }
    }

    @Autowired
    private MfImportService importService;

    @Autowired
    private MfReconciliationService reconciliationService;

    @Autowired
    private MfImportBatchRepository batchRepository;

    @Autowired
    private MfTmpFolioRepository tmpFolioRepository;

    @Autowired
    private MfTmpSchemeRepository tmpSchemeRepository;

    @Autowired
    private MfTmpTransactionRepository tmpTransactionRepository;

    @Autowired
    private MfTmpValuationRepository tmpValuationRepository;

    @Autowired
    private MfTmpNomineeRepository tmpNomineeRepository;

    @Autowired
    private MfTmpPortfolioSummaryRepository tmpSummaryRepository;

    @Autowired
    private MfAmcRepository amcRepository;

    @Autowired
    private MfFolioRepository folioRepository;

    @Autowired
    private MfFolioSchemeRepository schemeRepository;

    @Autowired
    private MfFolioSchemeTransactionRepository transactionRepository;

    @Autowired
    private MfFolioSchemeValuationRepository valuationRepository;

    @Autowired
    private MfFolioSchemeNomineeRepository nomineeRepository;

    @Autowired
    private MfPortfolioSummaryRepository summaryRepository;

    /**
     * Every test starts from an empty portfolio for {@link #OWNER}, so a first
     * import really is the first one whatever ran before it.
     */
    @BeforeEach
    void clearOwnersData() {
        batchRepository.deleteAll(batchRepository.findByPanOrderByUploadedAtDesc(OWNER));
        tmpTransactionRepository.deleteByOwner(OWNER);
        tmpValuationRepository.deleteByOwner(OWNER);
        tmpNomineeRepository.deleteByOwner(OWNER);
        tmpSchemeRepository.deleteByOwner(OWNER);
        tmpFolioRepository.deleteByOwner(OWNER);
        tmpSummaryRepository.deleteByOwner(OWNER);
        transactionRepository.deleteByOwner(OWNER);
        valuationRepository.deleteByOwner(OWNER);
        nomineeRepository.deleteByOwner(OWNER);
        schemeRepository.deleteByOwner(OWNER);
        folioRepository.deleteByOwner(OWNER);
        summaryRepository.deleteByOwner(OWNER);
    }

    // ------------------------------------------------------------- passwords

    @Test
    void protectedReportAsksForAPassword() throws Exception {
        byte[] pdf = Files.readAllBytes(REPORT);
        KfCasParser parser = new KfCasParser();

        KfCasParser.PasswordRequiredException missing = assertThrows(
                KfCasParser.PasswordRequiredException.class, () -> parser.parse(pdf, null));
        assertTrue(missing.getMessage().contains("password protected"), missing.getMessage());

        KfCasParser.PasswordRequiredException blank = assertThrows(
                KfCasParser.PasswordRequiredException.class, () -> parser.parse(pdf, "  "));
        assertFalse(blank.getMessage().isBlank());

        assertThrows(KfCasParser.PasswordMismatchException.class, () -> parser.parse(pdf, "wrong"));
        assertNotNull(parser.parse(pdf, PASSWORD));
    }

    // ------------------------------------------------------------------ flow

    @Test
    void uploadReconcileAndAccept() throws Exception {
        byte[] pdf = Files.readAllBytes(REPORT);

        // 1 - a first upload stages the report and books the ADJUSTED rows
        Map<String, Object> first = importService.importFile(OWNER, REPORT.getFileName().toString(), pdf, PASSWORD);
        MfImportBatch batch = (MfImportBatch) first.get("batch");
        assertTrue(Boolean.TRUE.equals(first.get("firstImport")));
        assertTrue(batch.getTxnCount() > 2000, "every ledger row is staged: " + batch.getTxnCount());
        assertTrue(batch.getAdjustedCount() > 0, "a first import books the adjusted rows");
        assertTrue(batch.getFolioCount() > 0 && batch.getSchemeCount() > 0);
        // MF_V2 1 - the period shown is the one printed in the page header
        assertEquals(LocalDate.of(2007, 1, 25), batch.getReportFrom(),
                "the period starts where the statement prints it");
        assertEquals(LocalDate.of(2026, 10, 25), batch.getReportTo());
        assertFalse(tmpFolioRepository.findByOwnerPanOrderByFolioNo(OWNER).isEmpty());
        assertFalse(tmpTransactionRepository.findByOwnerPan(OWNER).isEmpty());
        assertTrue(tmpTransactionRepository.findByOwnerPan(OWNER).stream()
                .anyMatch(t -> "ADJUSTED".equals(t.getType())), "staging holds the adjusted rows");
        assertTrue(tmpTransactionRepository.findByOwnerPan(OWNER).stream()
                .allMatch(t -> t.getTxnDate() != null), "every staged row carries a date");

        // 2 - the five reconciliation reports of MF.md 12
        Map<String, Object> report = reconciliationService.report(batch.getId(), OWNER);
        assertNotNull(report.get("status"));
        assertFalse(Boolean.TRUE.equals(report.get("hasAcceptedData")), "nothing is accepted yet");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) report.get("reports");
        assertEquals(5, reports.size(), "reports 1 to 5");
        for (Map<String, Object> one : reports) {
            assertNotNull(one.get("rows"), "report " + one.get("id") + " carries rows");
            assertNotNull(one.get("status"), "report " + one.get("id") + " carries a colour code");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) report.get("counts");
        assertEquals(tmpTransactionRepository.findByOwnerPan(OWNER).size() - batch.getAdjustedCount(),
                counts.get("newTransactions"), "every non adjusted row of a first import is new");
        assertEquals(0, counts.get("missingTransactions"), "a first import loses nothing");

        // MF_V2 2/4 - reports 1 and 3 carry the AMC of every row, and the
        // scheme column holds "folio number | scheme name", so the screen can
        // filter on date / folio # / scheme / AMC.
        List<Map<String, Object>> newTxns = rowsOf(reports, 1);
        assertFalse(newTxns.isEmpty(), "a first import brings new transactions");
        assertTrue(newTxns.stream().allMatch(r -> r.get("amc") != null),
                "every new transaction carries its folio's AMC");
        assertTrue(newTxns.stream().allMatch(r -> String.valueOf(r.get("scheme")).contains(" | ")),
                "the scheme column carries folio and scheme name");
        List<Map<String, Object>> adjusted = rowsOf(reports, 3);
        assertFalse(adjusted.isEmpty(), "the first import moves the adjusted amounts");
        assertTrue(adjusted.stream().allMatch(r -> r.get("amc") != null),
                "every adjusted row carries its folio's AMC");

        // 3 - accepting copies the staged report into the permanent tables
        Map<String, Object> accepted = importService.accept(batch.getId(), OWNER);
        assertEquals("ACCEPTED", ((MfImportBatch) accepted.get("batch")).getStatus());
        assertFalse(folioRepository.findByOwnerPanOrderByFolioNo(OWNER).isEmpty());
        assertTrue(folioRepository.findByOwnerPanOrderByFolioNo(OWNER).stream()
                .allMatch(folio -> folio.getAmcId() != null
                        && "DETAIL_STATEMENT".equals(folio.getRefreshFrom())),
                "accepted detail folios reference normalized AMC and record their refresh source");
        assertTrue(amcRepository.count() > 0, "detail import populates the normalized AMC table");
        assertFalse(schemeRepository.findByOwnerPan(OWNER).isEmpty());
        assertTrue(schemeRepository.findByOwnerPan(OWNER).stream()
                .allMatch(scheme -> scheme.getFolioReference() != null
                        && scheme.getFolioReference().getAmcId() != null
                        && "DETAIL_STATEMENT".equals(scheme.getRefreshFrom())),
                "accepted detail schemes resolve their AMC through their parent folio and record refresh source");
        assertFalse(transactionRepository.findByOwnerPan(OWNER).isEmpty());
        assertTrue(summaryRepository.findByOwnerPanOrderByAmc(OWNER).stream()
                .filter(row -> !row.isTotal())
                .allMatch(row -> row.getAmcId() != null
                        && "DETAIL_STATEMENT".equals(row.getRefreshFrom())
                        && "DETAIL_STATEMENT".equals(row.getMatchCondition())),
                "detail summaries carry AMC ID and source condition");
        assertEquals(batch.getTxnCount(), transactionRepository.findByOwnerPan(OWNER).size(),
                "accepted rows equal the staged rows");

        // 4 - the same report again: nothing new, nothing missing, unchanged adjustments
        Map<String, Object> second = importService.importFile(OWNER, REPORT.getFileName().toString(), pdf, PASSWORD);
        assertFalse(Boolean.TRUE.equals(second.get("firstImport")), "the data is already accepted");
        MfImportBatch secondBatch = (MfImportBatch) second.get("batch");
        assertEquals(0, secondBatch.getAdjustedCount(), "only a first import books adjusted rows");

        Map<String, Object> again = reconciliationService.report(secondBatch.getId(), OWNER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> againReports = (List<Map<String, Object>>) again.get("reports");
        assertEquals(0, rowCount(againReports, 1), "the same report brings no new transaction");
        assertEquals(0, rowCount(againReports, 3), "the adjusted amount is unchanged");
        assertEquals(0, rowCount(againReports, 4), "no transaction is missing");
        assertEquals(0, redRows(againReports, 5),
                "every total is inside the tolerance: " + rowsOf(againReports, 5));
        assertEquals("GREEN", again.get("status"),
                "re-reading the accepted statement reconciles cleanly: " + again.get("counts"));

        // The duplicate upload is green with no deltas. Discarding it clears
        // staging; the reconciliation endpoint must not then call every
        // accepted ledger row "missing" (MF_V4 1).
        Map<String, Object> cleanBeforeDiscard = reconciliationService.report(secondBatch.getId(), OWNER);
        assertEquals("GREEN", cleanBeforeDiscard.get("status"));
        importService.discard(secondBatch.getId(), OWNER);
        Map<String, Object> cleanAfterDiscard = reconciliationService.report(secondBatch.getId(), OWNER);
        assertEquals("GREEN", cleanAfterDiscard.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> discardedCounts = (Map<String, Object>) cleanAfterDiscard.get("counts");
        for (String key : List.of("newTransactions", "amcMismatches", "adjustedChanges",
                "missingTransactions", "failedChecks")) {
            assertEquals(0, ((Number) discardedCounts.get(key)).intValue(), key);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> discardedReports = (List<Map<String, Object>>) cleanAfterDiscard.get("reports");
        assertEquals(5, discardedReports.size());
        assertTrue(discardedReports.stream().allMatch(one -> "green".equals(one.get("status"))
                        && ((List<?>) one.get("rows")).isEmpty()),
                "a discarded upload has no reconciliation rows");

        // the import history keeps both uploads (MF.md 17)
        assertEquals(2, importService.listBatches(OWNER).size());
        assertNotNull(importService.parsedJson(secondBatch.getId(), OWNER));
    }

    // -------------------------------------------------------------- MF_V3 4

    @Test
    void anotherClientsStatementIsRejected() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> importService.importFile("ZZZZZ9999Z", REPORT.getFileName().toString(),
                        readAllBytes(), PASSWORD));
        assertTrue(error.getMessage().contains("belongs to PAN"), error.getMessage());
        assertTrue(error.getMessage().contains("AIGPR6090R"), error.getMessage());
        // the check runs before anything is written, so no staging happened
        assertTrue(batchRepository.findByPanOrderByUploadedAtDesc("ZZZZZ9999Z").isEmpty());
    }

    private static byte[] readAllBytes() {
        try {
            return Files.readAllBytes(REPORT);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void panMatchingIgnoresTheCaseTheStatementPrintsItIn() throws Exception {
        Map<String, Object> upload = importFile("aigpr6090r", REPORT);
        assertNotNull(upload.get("batch"));
    }

    // -------------------------------------------------------------- MF_V3 2

    /**
     * A statement whose period sits inside an accepted one adds nothing: every
     * report is green and empty, and accepting it changes no table at all
     * (MF_V3 2/3). The statement printed an accepted transaction the data on
     * file has lost - the one case that must still turn red - is checked at
     * the end.
     */
    @Test
    void subsetStatementReconcilesCleanAgainstTheAcceptedSuperset() throws Exception {
        Map<String, Object> first = importFile(OWNER, SUPERSET);
        MfImportBatch superset = (MfImportBatch) first.get("batch");
        assertEquals(LocalDate.of(2001, 9, 1), superset.getReportFrom());
        assertEquals(LocalDate.of(2026, 10, 25), superset.getReportTo());
        importService.accept(superset.getId(), OWNER);
        long acceptedTxns = transactionRepository.findByOwnerPan(OWNER).size();
        long acceptedSchemes = schemeRepository.findByOwnerPan(OWNER).size();
        long acceptedSummaryRows = summaryRepository.findByOwnerPanOrderByAmc(OWNER).size();

        Map<String, Object> second = importFile(OWNER, SUBSET);
        MfImportBatch subset = (MfImportBatch) second.get("batch");
        assertEquals(LocalDate.of(2025, 5, 14), subset.getReportFrom());
        assertEquals(LocalDate.of(2025, 10, 23), subset.getReportTo());
        assertFalse(Boolean.TRUE.equals(second.get("firstImport")), "the data is already accepted");

        Map<String, Object> report = reconciliationService.report(subset.getId(), OWNER);
        @SuppressWarnings("unchecked")
        Map<String, Object> coverage = (Map<String, Object>) report.get("coverage");
        assertTrue(Boolean.TRUE.equals(coverage.get("covered")),
                "the accepted statement spans the upload: " + coverage);
        assertEquals("GREEN", report.get("status"),
                "a subset of the accepted statement reconciles clean: " + report.get("counts"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) report.get("counts");
        for (String key : List.of("newTransactions", "missingTransactions", "adjustedChanges",
                "amcMismatches", "failedChecks")) {
            assertEquals(0, ((Number) counts.get(key)).intValue(), key);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) report.get("reports");
        assertEquals(5, reports.size());
        for (Map<String, Object> one : reports) {
            assertEquals(0, ((List<?>) one.get("rows")).size(),
                    "report " + one.get("id") + " carries no record");
        }

        // accepting it writes nothing and leaves the summary alone (MF_V3 6)
        Map<String, Object> accepted = importService.accept(subset.getId(), OWNER);
        assertEquals(Boolean.FALSE, accepted.get("summaryUpdated"),
                "an older statement may not move the summary");
        assertEquals(acceptedTxns, transactionRepository.findByOwnerPan(OWNER).size());
        assertEquals(acceptedSchemes, schemeRepository.findByOwnerPan(OWNER).size());
        assertTrue(summaryRepository.findByOwnerPanOrderByAmc(OWNER).size() > 0);
        assertTrue(summaryRepository.findByOwnerPanOrderByAmc(OWNER).stream()
                .filter(row -> !row.isTotal())
                .allMatch(row -> "DETAIL_STATEMENT".equals(row.getRefreshFrom())
                        && "DETAIL_STATEMENT".equals(row.getMatchCondition())),
                "MF_V5 refreshes the snapshot and marks it as sourced from the accepted detail report");
    }

    /** MF_V3 3 - the one red case: a row inside the period the data on file lost. */
    @Test
    void acceptedTransactionLostInsideTheUploadsPeriodStaysRed() throws Exception {
        MfImportBatch superset = (MfImportBatch) importFile(OWNER, SUPERSET).get("batch");
        importService.accept(superset.getId(), OWNER);
        MfImportBatch subset = (MfImportBatch) importFile(OWNER, SUBSET).get("batch");

        MfFolioSchemeTransaction lost = transactionRepository.findByOwnerPan(OWNER).stream()
                .filter(t -> t.getTxnDate() != null
                        && !t.getTxnDate().isBefore(LocalDate.of(2025, 5, 14))
                        && !t.getTxnDate().isAfter(LocalDate.of(2025, 10, 23)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no ledger row inside the subset period"));
        transactionRepository.deleteById(lost.getId());

        Map<String, Object> report = reconciliationService.report(subset.getId(), OWNER);
        assertEquals("RED", report.get("status"));
        List<Map<String, Object>> missing = rowsOf(reportsOf(report), 4);
        assertFalse(missing.isEmpty(), "the lost transaction is reported");
        assertTrue(missing.stream().allMatch(r -> r.get("amc") != null),
                "every report 4 row carries its folio's AMC for the filter");
        assertTrue(missing.stream().allMatch(r -> String.valueOf(r.get("scheme")).contains(" | ")),
                "the scheme column carries folio and scheme name");
    }

    // -------------------------------------------------------------- utilities

    private Map<String, Object> importFile(String owner, Path statement) throws Exception {
        return importService.importFile(owner, statement.getFileName().toString(),
                Files.readAllBytes(statement), PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> reportsOf(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("reports");
    }

    @SuppressWarnings("unchecked")
    private static long rowCount(List<Map<String, Object>> reports, int reportId) {
        return rowsOf(reports, reportId).size();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(List<Map<String, Object>> reports, int reportId) {
        return reports.stream()
                .filter(r -> Integer.valueOf(reportId).equals(r.get("id")))
                .findFirst()
                .map(r -> (List<Map<String, Object>>) r.get("rows"))
                .orElse(List.of());
    }

    /** Report 5 always prints its checks; only a red one is a failure. */
    private static long redRows(List<Map<String, Object>> reports, int reportId) {
        return rowsOf(reports, reportId).stream().filter(r -> "red".equals(r.get("tone"))).count();
    }
}
