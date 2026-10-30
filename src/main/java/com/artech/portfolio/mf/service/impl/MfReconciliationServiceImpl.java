package com.artech.portfolio.mf.service.impl;

import com.artech.portfolio.mf.entity.MfFolio;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import com.artech.portfolio.mf.entity.tmp.MfTmpFolio;
import com.artech.portfolio.mf.entity.tmp.MfTmpPortfolioSummary;
import com.artech.portfolio.mf.entity.tmp.MfTmpScheme;
import com.artech.portfolio.mf.entity.tmp.MfTmpTransaction;
import com.artech.portfolio.mf.entity.tmp.MfTmpValuation;
import com.artech.portfolio.mf.parser.TxnClassifier;
import com.artech.portfolio.mf.repository.MfFolioRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeValuationRepository;
import com.artech.portfolio.mf.repository.MfImportBatchRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpFolioRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpPortfolioSummaryRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpSchemeRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpTransactionRepository;
import com.artech.portfolio.mf.repository.tmp.MfTmpValuationRepository;
import com.artech.portfolio.mf.service.MfReconciliationService;
import com.artech.portfolio.mf.util.MfTxnIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The five reconciliation reports of MF.md 12, read straight out of the two
 * table sets - the temporary upload against the accepted snapshot - so the user
 * sees what an accept would change before it happens (MF.md 13).
 *
 * <p>Nothing here is statement specific: rows are matched by their own identity
 * (folio + scheme + date + type + description + numbers) and every threshold
 * comes from the properties file (MF.md 12e/15). Both sides are loaded into the
 * same neutral snapshot, so the reports never care which table a row came
 * from.</p>
 */
@Service
public class MfReconciliationServiceImpl implements MfReconciliationService {

    private final MfImportBatchRepository batchRepository;

    private final MfTmpPortfolioSummaryRepository tmpSummaryRepository;
    private final MfTmpFolioRepository tmpFolioRepository;
    private final MfTmpSchemeRepository tmpSchemeRepository;
    private final MfTmpValuationRepository tmpValuationRepository;
    private final MfTmpTransactionRepository tmpTransactionRepository;

    private final MfPortfolioSummaryRepository summaryRepository;
    private final MfFolioRepository folioRepository;
    private final MfFolioSchemeRepository schemeRepository;
    private final MfFolioSchemeValuationRepository valuationRepository;
    private final MfFolioSchemeTransactionRepository transactionRepository;

    /** MF.md 12e - tolerances (in percent) read from application.yaml. */
    private final double unitsTolerance;
    private final double costTolerance;
    private final double marketTolerance;

    public MfReconciliationServiceImpl(MfImportBatchRepository batchRepository,
                                       MfTmpPortfolioSummaryRepository tmpSummaryRepository,
                                       MfTmpFolioRepository tmpFolioRepository,
                                       MfTmpSchemeRepository tmpSchemeRepository,
                                       MfTmpValuationRepository tmpValuationRepository,
                                       MfTmpTransactionRepository tmpTransactionRepository,
                                       MfPortfolioSummaryRepository summaryRepository,
                                       MfFolioRepository folioRepository,
                                       MfFolioSchemeRepository schemeRepository,
                                       MfFolioSchemeValuationRepository valuationRepository,
                                       MfFolioSchemeTransactionRepository transactionRepository,
                                       @Value("${mf.reconciliation.tolerance-units-percent:0.5}") double unitsTolerance,
                                       @Value("${mf.reconciliation.tolerance-cost-percent:0.5}") double costTolerance,
                                       @Value("${mf.reconciliation.tolerance-market-percent:0.5}") double marketTolerance) {
        this.batchRepository = batchRepository;
        this.tmpSummaryRepository = tmpSummaryRepository;
        this.tmpFolioRepository = tmpFolioRepository;
        this.tmpSchemeRepository = tmpSchemeRepository;
        this.tmpValuationRepository = tmpValuationRepository;
        this.tmpTransactionRepository = tmpTransactionRepository;
        this.summaryRepository = summaryRepository;
        this.folioRepository = folioRepository;
        this.schemeRepository = schemeRepository;
        this.valuationRepository = valuationRepository;
        this.transactionRepository = transactionRepository;
        this.unitsTolerance = unitsTolerance;
        this.costTolerance = costTolerance;
        this.marketTolerance = marketTolerance;
    }

    @Override
    public Map<String, Object> report(Long batchId, String ownerPan) {
        MfImportBatch batch = batchRepository.findByIdAndPan(batchId, ownerPan).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Import " + batchId + " was not found for this client"));

        Snapshot accepted = loadPermanent(ownerPan);
        if ("DISCARDED".equals(batch.getStatus())) {
            // Discard removes the staged rows. Do not compare that intentionally
            // empty staging area with accepted history: doing so would falsely
            // report every accepted transaction as missing (MF_V4 1).
            List<Map<String, Object>> emptyReports = List.of(
                    unchanged(1, "New transactions in this upload", "This upload was discarded; no rows remain to reconcile."),
                    unchanged(2, "AMC summary against its transactions", "This upload was discarded; no rows remain to reconcile."),
                    unchanged(3, "ADJUSTED amount changes", "This upload was discarded; no rows remain to reconcile."),
                    unchanged(4, "Missing / out of sequence transactions", "This upload was discarded; no rows remain to reconcile."),
                    unchanged(5, "Totals against the summary (tolerance)", "This upload was discarded; no rows remain to reconcile."));
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("batch", batch);
            empty.put("hasAcceptedData", !accepted.schemes.isEmpty());
            empty.put("coverage", Map.of("covered", false, "note", ""));
            empty.put("status", "GREEN");
            empty.put("counts", Map.of("newTransactions", 0, "amcMismatches", 0,
                    "adjustedChanges", 0, "missingTransactions", 0, "failedChecks", 0));
            empty.put("reports", emptyReports);
            empty.put("tolerance", Map.of("unitsPercent", unitsTolerance,
                    "costPercent", costTolerance, "marketPercent", marketTolerance));
            return empty;
        }
        Snapshot temp = loadTemporary(ownerPan);
        Coverage coverage = coverage(batch, ownerPan);

        Map<String, Object> report1 = report1NewTransactions(temp, accepted);
        Map<String, Object> report2;
        Map<String, Object> report3;
        Map<String, Object> report5;
        if (coverage.covered) {
            // MF_V3 2/3 - the accepted statement already spans this one, so its
            // summary and its valuations are not what this upload is judged by:
            // the totals and the adjusted amounts cannot move, and the only
            // thing left to review is the transactions themselves.
            report2 = unchanged(2, "AMC summary against its transactions", coverage.note);
            report3 = unchanged(3, "ADJUSTED amount changes", coverage.note);
            report5 = unchanged(5, "Totals against the summary (tolerance)", coverage.note);
        } else {
            report2 = report2AmcSummary(temp, accepted);
            report3 = report3Adjusted(temp, accepted);
            report5 = report5Totals(temp, accepted);
        }
        Map<String, Object> report4 = report4Missing(temp, accepted, batch.getReportFrom(), batch.getReportTo());
        List<Map<String, Object>> reports = List.of(report1, report2, report3, report4, report5);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("newTransactions", rows(report1).size());
        counts.put("amcMismatches", failed(report2));
        counts.put("adjustedChanges", rows(report3).size());
        counts.put("missingTransactions", rows(report4).size());
        counts.put("failedChecks", failed(report5));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("batch", batch);
        body.put("hasAcceptedData", !accepted.schemes.isEmpty());
        body.put("coverage", coverage.asMap());
        String status = overallStatus(reports);
        body.put("status", status);
        if (!Objects.equals(batch.getValidStatus(), status)) {
            batch.setValidStatus(status);
            batchRepository.save(batch);
        }
        body.put("counts", counts);
        body.put("reports", reports);
        body.put("tolerance", Map.of("unitsPercent", unitsTolerance,
                "costPercent", costTolerance, "marketPercent", marketTolerance));
        return body;
    }

    /**
     * MF_V3 2/3 - how the period of this upload sits against the periods the
     * client already accepted. A statement whose period is fully inside the
     * accepted one is a subset of the data on file: it can still carry a
     * transaction the accepted statement is missing, but it can never change a
     * total, so the summary reports have nothing to say about it.
     */
    private Coverage coverage(MfImportBatch batch, String ownerPan) {
        List<MfImportBatch> accepted = batchRepository
                .findByPanAndStatusOrderByUploadedAtDesc(ownerPan, "ACCEPTED").stream()
                .filter(b -> !Objects.equals(b.getId(), batch.getId()))
                .toList();
        if (accepted.isEmpty()) {
            return new Coverage(false, "");
        }
        LocalDate acceptedFrom = null;
        LocalDate acceptedTo = null;
        for (MfImportBatch one : accepted) {
            if (one.getReportFrom() != null && (acceptedFrom == null || one.getReportFrom().isBefore(acceptedFrom))) {
                acceptedFrom = one.getReportFrom();
            }
            if (one.getReportTo() != null && (acceptedTo == null || one.getReportTo().isAfter(acceptedTo))) {
                acceptedTo = one.getReportTo();
            }
        }
        if (acceptedTo == null || batch.getReportFrom() == null || batch.getReportTo() == null) {
            return new Coverage(false, "");
        }
        boolean covered = !batch.getReportTo().isAfter(acceptedTo)
                && (acceptedFrom == null || !batch.getReportFrom().isBefore(acceptedFrom));
        if (!covered) {
            return new Coverage(false, "");
        }
        return new Coverage(true, "The accepted statement already covers the whole period of this upload ("
                + batch.getReportFrom() + " to " + batch.getReportTo()
                + "), so the totals and the adjusted amounts are unchanged.");
    }

    /** MF_V3 3 - a report with nothing to reconcile: green and without rows. */
    private static Map<String, Object> unchanged(int id, String name, String note) {
        Map<String, Object> report = report(id, name);
        finish(report, "green", note);
        return report;
    }

    // ------------------------------------------------------------ report 1

    /** MF.md 12a - rows of the upload the accepted data does not have yet. */
    private Map<String, Object> report1NewTransactions(Snapshot temp, Snapshot accepted) {
        Map<String, Object> report = report(1, "New transactions in this upload");
        for (Txn txn : temp.transactions) {
            if (txn.adjusted || accepted.txnKeys.contains(txn.identity)) {
                continue;
            }
            row(report, "green",
                    "scheme", txn.schemeKey,
                    "amc", txn.amc,
                    "date", str(txn.date),
                    "description", str(txn.description),
                    "amount", txn.amount,
                    "units", txn.units,
                    "type", str(txn.type));
        }
        finish(report, "green", rows(report).size() + " new transaction(s) of the report are not accepted yet");
        return report;
    }

    // ------------------------------------------------------------ report 2

    /**
     * MF.md 12b - every AMC line of the printed summary against the money
     * behind it: the accepted ledger plus the rows this upload adds, including
     * the ADJUSTED amount (MF.md 10). Market value is compared with the scheme
     * valuations of the upload, which is where the statement prints it
     * (MF.md 11).
     */
    private Map<String, Object> report2AmcSummary(Snapshot temp, Snapshot accepted) {
        Map<String, Object> report = report(2, "AMC summary against its transactions");

        Map<String, BigDecimal> ledger = new HashMap<>();
        for (Txn txn : effectiveLedger(temp, accepted)) {
            ledger.merge(txn.amc, nvl(txn.amount), BigDecimal::add);
        }
        Map<String, BigDecimal> market = new HashMap<>();
        for (Valuation value : temp.values.values()) {
            market.merge(value.amc(), nvl(value.market), BigDecimal::add);
        }

        for (Sum line : temp.summary) {
            if (line.total) {
                continue;
            }
            BigDecimal summaryCost = nvl(line.cost);
            BigDecimal ledgerCost = ledger.getOrDefault(line.amc, BigDecimal.ZERO);
            BigDecimal summaryMarket = nvl(line.market);
            BigDecimal valuationMarket = market.getOrDefault(line.amc, BigDecimal.ZERO);
            boolean costOk = within(summaryCost, ledgerCost, costTolerance);
            boolean marketOk = within(summaryMarket, valuationMarket, marketTolerance);
            row(report, costOk && marketOk ? "green" : "red",
                    "amc", line.amc,
                    "summaryCost", summaryCost,
                    "transactionCost", ledgerCost,
                    "costDifference", ledgerCost.subtract(summaryCost),
                    "summaryMarket", summaryMarket,
                    "valuationMarket", valuationMarket,
                    "marketDifference", valuationMarket.subtract(summaryMarket),
                    "costOk", costOk,
                    "marketOk", marketOk);
        }
        finish(report, failed(report) == 0 ? "green" : "red",
                failed(report) == 0
                        ? "Every AMC summary line matches the transactions behind it"
                        : "AMC summary lines that do not match their transactions");
        return report;
    }

    // ------------------------------------------------------------ report 3

    /**
     * MF.md 12c - the ADJUSTED amount of the accepted data against the one the
     * upload implies now. A longer period than the last upload brings older
     * transactions and moves the adjustment, so every change is highlighted.
     * The ledger the new adjustment aims at is the merged one - everything
     * already accepted plus what this upload adds - because that is the ledger
     * the summary has to tie to once the upload is accepted (MF.md 12e).
     */
    private Map<String, Object> report3Adjusted(Snapshot temp, Snapshot accepted) {
        Map<String, Object> report = report(3, "ADJUSTED amount changes");

        Map<String, BigDecimal> booked = new HashMap<>();
        for (Txn txn : accepted.transactions) {
            if (txn.adjusted) {
                booked.merge(txn.schemeKey, nvl(txn.amount), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> spentByScheme = new HashMap<>();
        for (Txn txn : effectiveLedger(temp, accepted)) {
            if (!txn.adjusted) {
                spentByScheme.merge(txn.schemeKey, nvl(txn.amount), BigDecimal::add);
            }
        }
        for (Scheme scheme : temp.schemes.values()) {
            BigDecimal spent = spentByScheme.getOrDefault(scheme.key, BigDecimal.ZERO);
            BigDecimal implied = nvl(scheme.cost).subtract(spent);
            BigDecimal was = booked.get(scheme.key);
            if (was != null && was.compareTo(implied) == 0) {
                continue;
            }
            if (was == null && implied.signum() == 0) {
                continue; // nothing booked, nothing to review
            }
            row(report, was == null ? "blue" : "amber",
                    "scheme", scheme.key,
                    "amc", scheme.amc,
                    "previous", was == null ? "not booked" : was,
                    "current", implied,
                    "difference", implied.subtract(was == null ? BigDecimal.ZERO : was),
                    "reason", was == null
                            ? "The adjustment is booked with this upload (first import)"
                            : "The upload changed the ledger behind the adjusted amount");
        }
        boolean empty = rows(report).isEmpty();
        finish(report, empty ? "green" : "amber",
                empty ? "Adjusted amounts are unchanged"
                        : "Adjusted amounts moved - review them before accepting");
        return report;
    }

    // ------------------------------------------------------------ report 4

    /**
     * MF.md 12d - missing transactions across the two date ranges: a row the
     * upload adds that is older than the accepted data's last transaction, and
     * an accepted row the upload no longer carries. Both are red so the gap can
     * be explained before accepting.
     *
     * <p>MF_V3 1 - the second half is read against the period the uploaded
     * statement prints: an accepted transaction outside it was never supposed
     * to be in this file, so it is not "missing" from it. Without that bound a
     * short statement - a five month window against a twenty five year one -
     * reports every older transaction of the portfolio as missing.</p>
     */
    private Map<String, Object> report4Missing(Snapshot temp, Snapshot accepted,
                                               LocalDate from, LocalDate to) {
        Map<String, Object> report = report(4, "Missing / out of sequence transactions");
        LocalDate lastAccepted = accepted.latestTxnDate;

        for (Txn txn : temp.transactions) {
            if (txn.adjusted || accepted.txnKeys.contains(txn.identity)) {
                continue;
            }
            if (lastAccepted != null && txn.date != null && txn.date.isBefore(lastAccepted)) {
                row(report, "red",
                        "reason", "Back dated - the accepted data already runs up to "
                                + lastAccepted + ", this transaction is older",
                        "where", "new upload only",
                        "scheme", txn.schemeKey,
                        "amc", txn.amc,
                        "date", str(txn.date),
                        "description", str(txn.description),
                        "amount", txn.amount,
                        "units", txn.units,
                        "type", str(txn.type));
            }
        }
        for (Txn txn : accepted.transactions) {
            if (txn.adjusted || temp.txnKeys.contains(txn.identity)) {
                continue;
            }
            if (txn.date == null || !withinPeriod(txn.date, from, to)) {
                continue; // MF_V3 1 - only the period of this statement is compared
            }
            row(report, "red",
                    "reason", "The accepted data has it, the new upload does not carry it",
                    "where", "accepted data only",
                    "scheme", txn.schemeKey,
                    "amc", txn.amc,
                    "date", str(txn.date),
                    "description", str(txn.description),
                    "amount", txn.amount,
                    "units", txn.units,
                    "type", str(txn.type));
        }
        boolean empty = rows(report).isEmpty();
        finish(report, empty ? "green" : "red",
                empty ? "No transaction is missing between the two uploads"
                        : "Transactions differ between the two uploads");
        return report;
    }

    /** True when the date falls inside the period the uploaded statement prints. */
    private static boolean withinPeriod(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    // ------------------------------------------------------------ report 5

    /** MF.md 12e - totals of the upload, green while inside the tolerance. */
    private Map<String, Object> report5Totals(Snapshot temp, Snapshot accepted) {
        Map<String, Object> report = report(5, "Totals against the summary (tolerance)");

        BigDecimal openUnits = BigDecimal.ZERO;
        BigDecimal closeUnits = BigDecimal.ZERO;
        for (Scheme scheme : temp.schemes.values()) {
            openUnits = openUnits.add(nvl(scheme.open));
            closeUnits = closeUnits.add(nvl(scheme.close));
        }
        BigDecimal txnUnits = BigDecimal.ZERO;
        BigDecimal txnCost = BigDecimal.ZERO;
        for (Txn txn : effectiveLedger(temp, accepted)) {
            txnUnits = txnUnits.add(nvl(txn.units));
            txnCost = txnCost.add(nvl(txn.amount));
        }
        BigDecimal summaryCost = summaryTotal(temp.summary, false);
        BigDecimal summaryMarket = summaryTotal(temp.summary, true);
        BigDecimal valuationCost = BigDecimal.ZERO;
        BigDecimal valuationMarket = BigDecimal.ZERO;
        for (Valuation value : temp.values.values()) {
            valuationCost = valuationCost.add(nvl(value.cost));
            valuationMarket = valuationMarket.add(nvl(value.market));
        }

        addRow(report, check("Total units", "opening balance of every scheme plus its ledger",
                openUnits.add(txnUnits), closeUnits, unitsTolerance));
        addRow(report, check("Total cost", "cost value of the printed summary",
                txnCost, summaryCost, costTolerance));
        addRow(report, check("Total cost (valuations)", "cost value of the scheme valuations",
                valuationCost, summaryCost, costTolerance));
        addRow(report, check("Total market value", "market value of the printed summary",
                valuationMarket, summaryMarket, marketTolerance));

        boolean ok = failed(report) == 0;
        finish(report, ok ? "green" : "red",
                ok ? "Totals are inside the configured tolerance"
                        : "Totals are outside the configured tolerance");
        return report;
    }

    private Map<String, Object> check(String name, String against, BigDecimal expected,
                                      BigDecimal actual, double tolerancePercent) {
        boolean ok = within(expected, actual, tolerancePercent);
        return Map.of(
                "tone", ok ? "green" : "red",
                "check", name,
                "against", against,
                "expected", expected,
                "actual", actual,
                "difference", actual.subtract(expected),
                "tolerancePercent", tolerancePercent,
                "ok", ok);
    }

    // -------------------------------------------------------------- loading

    private Snapshot loadTemporary(String ownerPan) {
        Snapshot snapshot = new Snapshot();
        Map<Long, String> folioNos = new HashMap<>();
        Map<Long, String> folioAmcs = new HashMap<>();
        for (MfTmpFolio folio : tmpFolioRepository.findByOwnerPanOrderByFolioNo(ownerPan)) {
            folioNos.put(folio.getId(), folio.getFolioNo());
            folioAmcs.put(folio.getId(), folio.getAmc());
        }
        Map<Long, String> keyBySchemeId = new HashMap<>();
        for (MfTmpScheme scheme : tmpSchemeRepository.findByOwnerPan(ownerPan)) {
            String key = schemeKey(folioNos.get(scheme.getFolioId()), scheme.getScheme());
            keyBySchemeId.put(scheme.getId(), key);
            String amc = folioAmcs.getOrDefault(scheme.getFolioId(), "");
            Scheme info = new Scheme(key, amc, scheme.getOpenUnits(), scheme.getCloseUnits());
            snapshot.schemes.put(key, info);
        }
        for (MfTmpValuation value : tmpValuationRepository.findBySchemeIdIn(keyBySchemeId.keySet())) {
            String key = keyBySchemeId.get(value.getSchemeId());
            Scheme scheme = snapshot.schemes.get(key);
            if (scheme != null && value.getCostValue() != null) {
                scheme.cost = value.getCostValue();
            }
            snapshot.values.put(key, new Valuation(key, scheme == null ? "" : scheme.amc,
                    value.getCostValue(), value.getMarketValue()));
        }
        for (MfTmpTransaction row : tmpTransactionRepository.findBySchemeIdIn(keyBySchemeId.keySet())) {
            String key = keyBySchemeId.get(row.getSchemeId());
            snapshot.add(new Txn(key, row.getTxnDate(), row.getType(), row.getDescription(),
                    row.getAmount(), row.getUnits(), amcOf(snapshot, key)));
        }
        for (MfTmpPortfolioSummary row : tmpSummaryRepository.findByOwnerPanOrderByAmc(ownerPan)) {
            snapshot.summary.add(new Sum(row.getAmc(), row.getCostValue(), row.getMarketValue(), row.isTotal()));
        }
        return snapshot;
    }

    private Snapshot loadPermanent(String ownerPan) {
        Snapshot snapshot = new Snapshot();
        Map<Long, String> folioNos = new HashMap<>();
        Map<Long, String> folioAmcs = new HashMap<>();
        for (MfFolio folio : folioRepository.findByOwnerPanOrderByFolioNo(ownerPan)) {
            folioNos.put(folio.getId(), folio.getFolioNo());
            folioAmcs.put(folio.getId(), folio.getAmc());
        }
        Map<Long, String> keyBySchemeId = new HashMap<>();
        for (MfFolioScheme scheme : schemeRepository.findByOwnerPan(ownerPan)) {
            String key = schemeKey(folioNos.get(scheme.getFolioId()), scheme.getScheme());
            keyBySchemeId.put(scheme.getId(), key);
            String amc = folioAmcs.getOrDefault(scheme.getFolioId(), "");
            Scheme info = new Scheme(key, amc, scheme.getOpenUnits(), scheme.getCloseUnits());
            snapshot.schemes.put(key, info);
        }
        for (MfFolioSchemeValuation value : valuationRepository.findBySchemeIdIn(keyBySchemeId.keySet())) {
            String key = keyBySchemeId.get(value.getSchemeId());
            Scheme scheme = snapshot.schemes.get(key);
            if (scheme != null && value.getCostValue() != null) {
                scheme.cost = value.getCostValue();
            }
            snapshot.values.put(key, new Valuation(key, scheme == null ? "" : scheme.amc,
                    value.getCostValue(), value.getMarketValue()));
        }
        for (MfFolioSchemeTransaction row : transactionRepository.findBySchemeIdIn(keyBySchemeId.keySet())) {
            String key = keyBySchemeId.get(row.getSchemeId());
            snapshot.add(new Txn(key, row.getTxnDate(), row.getType(), row.getDescription(),
                    row.getAmount(), row.getUnits(), amcOf(snapshot, key)));
        }
        for (MfPortfolioSummary row : summaryRepository.findByOwnerPanOrderByAmc(ownerPan)) {
            snapshot.summary.add(new Sum(row.getAmc(), row.getCostValue(), row.getMarketValue(), row.isTotal()));
        }
        return snapshot;
    }

    private static String amcOf(Snapshot snapshot, String schemeKey) {
        Scheme scheme = snapshot.schemes.get(schemeKey);
        return scheme == null ? "" : scheme.amc;
    }

    /**
     * The ledger as it would stand after accepting this upload: everything
     * already accepted (including the ADJUSTED rows only a first import books,
     * MF.md 10) plus the rows the upload adds. Reports 2 and 5 both measure
     * against this, so a re-upload - which restages no adjustment - still ties
     * to the printed summary.
     */
    private static List<Txn> effectiveLedger(Snapshot temp, Snapshot accepted) {
        List<Txn> ledger = new ArrayList<>(accepted.transactions);
        for (Txn txn : temp.transactions) {
            if (!accepted.txnKeys.contains(txn.identity)) {
                ledger.add(txn);
            }
        }
        return ledger;
    }

    // -------------------------------------------------------------- helpers

    /** Identity of a scheme across two uploads: its folio and its own name. */
    private static String schemeKey(String folioNo, String scheme) {
        return MfTxnIdentity.schemeKey(folioNo, scheme);
    }

    private static boolean within(BigDecimal expected, BigDecimal actual, double percent) {
        BigDecimal base = expected.abs().max(actual.abs());
        if (base.signum() == 0) {
            return true;
        }
        BigDecimal allowed = base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return actual.subtract(expected).abs().compareTo(allowed) <= 0;
    }

    private static BigDecimal summaryTotal(List<Sum> rows, boolean market) {
        for (Sum row : rows) {
            if (row.total) {
                return nvl(market ? row.market : row.cost);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Sum row : rows) {
            total = total.add(nvl(market ? row.market : row.cost));
        }
        return total;
    }

    // ------------------------------------------------------- report plumbing

    private static Map<String, Object> report(int id, String name) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", id);
        report.put("name", name);
        report.put("status", "green");
        report.put("note", "");
        report.put("rows", new ArrayList<Map<String, Object>>());
        return report;
    }

    /** Adds one coloured row; keys stay in insertion order for the screen. */
    @SuppressWarnings("unchecked")
    private static void row(Map<String, Object> report, String tone, Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tone", tone);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        ((List<Map<String, Object>>) report.get("rows")).add(row);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("rows");
    }

    /** Adds an already built row - the total checks build their own map. */
    @SuppressWarnings("unchecked")
    private static void addRow(Map<String, Object> report, Map<String, Object> built) {
        ((List<Map<String, Object>>) report.get("rows")).add(built);
    }

    private static long failed(Map<String, Object> report) {
        return rows(report).stream().filter(r -> "red".equals(r.get("tone"))).count();
    }

    private static void finish(Map<String, Object> report, String status, String note) {
        report.put("status", status);
        report.put("note", note);
    }

    private static String overallStatus(List<Map<String, Object>> reports) {
        if (reports.stream().anyMatch(r -> "red".equals(r.get("status")))) {
            return "RED";
        }
        return reports.stream().anyMatch(r -> "amber".equals(r.get("status"))) ? "AMBER" : "GREEN";
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ------------------------------------------------------------ row models

    /** One ledger row of either side, already carrying its scheme's AMC. */
    private static final class Txn {
        final String schemeKey;
        final LocalDate date;
        final String type;
        final String description;
        final BigDecimal amount;
        final BigDecimal units;
        final String amc;
        /** true for the rows the first import books (MF.md 10). */
        final boolean adjusted;
        /** Identity of the row across two uploads (MF.md 12a). */
        final String identity;

        Txn(String schemeKey, LocalDate date, String type, String description,
            BigDecimal amount, BigDecimal units, String amc) {
            this.schemeKey = schemeKey;
            this.date = date;
            this.type = type;
            this.description = description;
            this.amount = amount;
            this.units = units;
            this.amc = amc;
            this.adjusted = TxnClassifier.TYPE_ADJUSTED.equals(type);
            this.identity = MfTxnIdentity.of(schemeKey, date, type, description, amount, units);
        }
    }

    /** The scheme both sides are compared under, with its printed totals. */
    private static final class Scheme {
        final String key;
        final String amc;
        final BigDecimal open;
        final BigDecimal close;
        /** cost value of the scheme's valuation - the side ADJUSTED aims at. */
        BigDecimal cost = BigDecimal.ZERO;

        Scheme(String key, String amc, BigDecimal open, BigDecimal close) {
            this.key = key;
            this.amc = amc;
            this.open = open;
            this.close = close;
        }
    }

    private record Valuation(String key, String amc, BigDecimal cost, BigDecimal market) {
    }

    private record Sum(String amc, BigDecimal cost, BigDecimal market, boolean total) {
    }

    /** MF_V3 2/3 - does the accepted data already span the period of this upload? */
    private record Coverage(boolean covered, String note) {
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("covered", covered);
            map.put("note", note);
            return map;
        }
    }

    /** Both sides of the comparison, keyed by the identity of the rows. */
    private static final class Snapshot {
        final List<Txn> transactions = new ArrayList<>();
        final Set<String> txnKeys = new HashSet<>();
        final Map<String, Scheme> schemes = new LinkedHashMap<>();
        final Map<String, Valuation> values = new LinkedHashMap<>();
        final List<Sum> summary = new ArrayList<>();
        LocalDate latestTxnDate;

        void add(Txn txn) {
            transactions.add(txn);
            txnKeys.add(txn.identity);
            if (txn.date != null && (latestTxnDate == null || txn.date.isAfter(latestTxnDate))) {
                latestTxnDate = txn.date;
            }
        }
    }
}
