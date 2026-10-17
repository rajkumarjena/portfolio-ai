package com.artech.portfolio.mf.service.impl;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.mf.entity.MfHeader;
import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.entity.MfImportLine;
import com.artech.portfolio.mf.entity.MfScheme;
import com.artech.portfolio.mf.entity.MfTransaction;
import com.artech.portfolio.mf.parser.CasReportParser;
import com.artech.portfolio.mf.parser.CsvCasParser;
import com.artech.portfolio.mf.parser.ParsedCasReport;
import com.artech.portfolio.mf.parser.PdfCasParser;
import com.artech.portfolio.mf.parser.XlsxCasParser;
import com.artech.portfolio.mf.repository.MfHeaderRepository;
import com.artech.portfolio.mf.repository.MfImportBatchRepository;
import com.artech.portfolio.mf.repository.MfImportLineRepository;
import com.artech.portfolio.mf.repository.MfSchemeRepository;
import com.artech.portfolio.mf.repository.MfTransactionRepository;
import com.artech.portfolio.mf.service.MfImportService;
import com.artech.portfolio.mf.util.MfConstants;
import com.artech.portfolio.mf.util.MfUtil;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reconciliation workflow behind the MF Central import screens (MF.md 14-16,
 * 20, 22, 23, 25).
 *
 *   upload  -> parse into MfImportLine rows (nothing touches the portfolio)
 *   validate-> header/detail level checks, dedupe, color codes
 *   remap   -> point unregistered schemes at the master list
 *   accept  -> only now MfHeader / MfTransaction are written
 */
@Service
public class MfImportServiceImpl implements MfImportService {

    private static final double TOTAL_TOL = 0.005; // 0.5% / 1 rupee whichever is larger

    private final MfImportBatchRepository batchRepository;
    private final MfImportLineRepository lineRepository;
    private final MfSchemeRepository schemeRepository;
    private final MfHeaderRepository headerRepository;
    private final MfTransactionRepository transactionRepository;
    private final ClientRepository clientRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public MfImportServiceImpl(MfImportBatchRepository batchRepository,
                               MfImportLineRepository lineRepository,
                               MfSchemeRepository schemeRepository,
                               MfHeaderRepository headerRepository,
                               MfTransactionRepository transactionRepository,
                               ClientRepository clientRepository) {
        this.batchRepository = batchRepository;
        this.lineRepository = lineRepository;
        this.schemeRepository = schemeRepository;
        this.headerRepository = headerRepository;
        this.transactionRepository = transactionRepository;
        this.clientRepository = clientRepository;
    }

    // ----------------------------------------------------------------- upload

    @Override
    @Transactional
    public Map<String, Object> importFile(String pan, String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new RuntimeException("The uploaded file is empty");
        }
        String type = CasReportParser.detectType(fileName);
        CasReportParser parser = switch (type) {
            case MfConstants.FILE_XLSX -> new XlsxCasParser();
            case MfConstants.FILE_PDF -> new PdfCasParser();
            default -> new CsvCasParser();
        };
        ParsedCasReport rep;
        try {
            rep = parser.parse(fileName, data);
        } catch (Exception e) {
            throw new RuntimeException("Could not read " + type + " report: " + e.getMessage());
        }

        MfImportBatch batch = new MfImportBatch();
        batch.setPan(pan);
        batch.setFileName(fileName);
        batch.setFileType(type);
        batch.setReportFrom(rep.reportFrom);
        batch.setReportTo(rep.reportTo);
        batch.setClientName(rep.clientName);
        batch.setReportPan(rep.pan);
        batch.setRptInvested(rep.rptInvested);
        batch.setRptCurrent(rep.rptCurrent);
        batch.setRptProfit(rep.rptProfit);
        batch.setRptUnits(rep.rptUnits);
        batch.setRptHoldingsCount(rep.holdings.size());
        batch.setRptTxnCount(rep.transactions.size());
        batch = batchRepository.save(batch);

        // scheme -> (amc, folio) as printed in this statement, for enriching
        // transaction rows which only carry the scheme name
        Map<String, ParsedCasReport.Holding> byScheme = new HashMap<>();
        for (ParsedCasReport.Holding h : rep.holdings) {
            byScheme.putIfAbsent(h.schemeName.toLowerCase(Locale.ROOT), h);
        }

        List<MfImportLine> lines = new ArrayList<>();
        int no = 0;
        Set<String> seen = new HashSet<>();
        for (ParsedCasReport.Holding h : rep.holdings) {
            MfImportLine l = new MfImportLine();
            l.setBatchId(batch.getId());
            l.setLineNo(++no);
            l.setLineType(MfConstants.LINE_HOLDING);
            l.setSchemeName(trim(h.schemeName));
            l.setAmcName(trim(h.amcName));
            l.setCategory(trim(h.category));
            l.setFolioNo(trim(h.folioNo));
            l.setInvestedValue(h.invested);
            l.setCurrentValue(h.current);
            l.setReturns(h.ret);
            l.setUnits(h.units);
            l.setDedupeKey(MfUtil.dedupeKey(pan, l.getSchemeName(), l.getAmcName(), l.getFolioNo(),
                    null, MfConstants.LINE_HOLDING, l.getUnits(), l.getInvestedValue()));
            lines.add(l);
        }
        for (ParsedCasReport.Txn t : rep.transactions) {
            MfImportLine l = new MfImportLine();
            l.setBatchId(batch.getId());
            l.setLineNo(++no);
            l.setLineType(MfConstants.LINE_TRANSACTION);
            l.setSchemeName(trim(t.schemeName));
            l.setDescription(trim(t.description));
            l.setTxnDate(t.date);
            l.setNav(t.nav);
            l.setUnits(t.units);
            l.setAmount(t.amount);
            l.setTxnType(inferTxnType(t.description, t.units, t.amount));

            // enrich amc/category from this statement's holdings of that scheme
            ParsedCasReport.Holding src = byScheme.getOrDefault(
                    l.getSchemeName().toLowerCase(Locale.ROOT), null);
            if (src != null) {
                l.setAmcName(trim(src.amcName));
                l.setCategory(trim(src.category));
            } else {
                List<MfScheme> byName = schemeRepository.findBySchemeNameIgnoreCase(l.getSchemeName());
                if (byName.size() == 1) {
                    l.setAmcName(byName.get(0).getAmcName());
                }
            }
            // folio: exact when the format carries it (PDF prints folio sections);
            // the xlsx/csv transaction sheets have no folio column, so they fall
            // back to the scheme's first holding (best effort for that format)
            if (t.folioNo != null && !t.folioNo.isBlank()) {
                l.setFolioNo(trim(t.folioNo));
            } else if (src != null) {
                l.setFolioNo(trim(src.folioNo));
            }
            // normalise outflows so aggregates reduce correctly (MF.md 10)
            if (MfConstants.TXN_REDEMPTION.equals(l.getTxnType())
                    || MfConstants.TXN_SWITCH_OUT.equals(l.getTxnType())) {
                if (l.getUnits() > 0) l.setUnits(-l.getUnits());
                if (l.getAmount() > 0) l.setAmount(-l.getAmount());
            }
            l.setDedupeKey(MfUtil.dedupeKey(pan, l.getSchemeName(), l.getAmcName(), l.getFolioNo(),
                    l.getTxnDate(), l.getTxnType(), l.getUnits(), l.getAmount()));
            lines.add(l);
        }
        lineRepository.saveAll(lines);

        return validate(batch.getId(), pan);
    }

    // ----------------------------------------------------------------- lookup

    @Override
    public List<MfImportBatch> listBatches(String pan) {
        return batchRepository.findByPanOrderByUploadedAtDesc(pan);
    }

    @Override
    public Map<String, Object> getBatch(Long id, String pan) {
        MfImportBatch batch = loadBatch(id, pan);
        List<MfImportLine> lines = lineRepository.findByBatchIdOrderByLineNoAsc(id);
        return buildResponse(batch, lines, pan);
    }

    @Override
    public void discard(Long id, String pan) {
        MfImportBatch batch = loadBatch(id, pan);
        if (MfConstants.BATCH_ACCEPTED.equals(batch.getStatus())) {
            throw new RuntimeException("An accepted batch cannot be discarded - its rows are already in the portfolio");
        }
        for (MfImportLine l : lineRepository.findByBatchIdOrderByLineNoAsc(id)) {
            lineRepository.delete(l);
        }
        batchRepository.delete(batch);
    }

    // --------------------------------------------------------------- validate

    @Override
    @Transactional
    public Map<String, Object> validate(Long id, String pan) {
        MfImportBatch batch = loadBatch(id, pan);
        List<MfImportLine> lines = lineRepository.findByBatchIdOrderByLineNoAsc(id);
        Set<String> seenInReport = new HashSet<>();

        int nNew = 0, nExists = 0, nNewScheme = 0, nMapped = 0, nError = 0, nSkipped = 0;
        int holding = 0, txnLines = 0;

        for (MfImportLine l : lines) {
            if (MfConstants.LINE_HOLDING.equals(l.getLineType())) holding++;
            else txnLines++;

            if (MfConstants.LINE_ERROR.equals(l.getStatus())) {
                nError++;
                continue;
            }
            if (l.getTargetSchemeId() != null) {
                l.setStatus(MfConstants.LINE_MAPPED);
                schemeRepository.findById(l.getTargetSchemeId()).ifPresent(s ->
                        l.setMessage("Remapped to " + s.getSchemeName() + " (" + s.getAmcName() + ")"));
                nMapped++;
                continue;
            }
            classify(pan, l, seenInReport);
            switch (l.getStatus()) {
                case MfConstants.LINE_NEW -> nNew++;
                case MfConstants.LINE_EXISTS -> nExists++;
                case MfConstants.LINE_NEW_SCHEME -> nNewScheme++;
                case MfConstants.LINE_SKIPPED -> nSkipped++;
                default -> nError++;
            }
        }
        lineRepository.saveAll(lines);

        batch.setNewCount(nNew);
        batch.setExistsCount(nExists);
        batch.setNewSchemeCount(nNewScheme);
        batch.setMappedCount(nMapped);
        batch.setErrorCount(nError);
        batch.setHoldingCount(holding);
        batch.setTxnLineCount(txnLines);

        // ---- header level checks (MF.md 15) --------------------------------
        double inv = 0, cur = 0, units = 0;
        for (MfImportLine l : lines) {
            if (!MfConstants.LINE_HOLDING.equals(l.getLineType())) continue;
            if (MfConstants.LINE_ERROR.equals(l.getStatus())) continue;
            inv += l.getInvestedValue();
            cur += l.getCurrentValue();
            units += l.getUnits();
        }
        List<Map<String, Object>> checks = new ArrayList<>();
        if (batch.getRptInvested() != 0) {
            checks.add(totalCheck("Total investments vs report header",
                    batch.getRptInvested(), inv));
        }
        if (batch.getRptCurrent() != 0) {
            checks.add(totalCheck("Current portfolio value vs report header",
                    batch.getRptCurrent(), cur));
        }
        if (batch.getRptProfit() != 0) {
            checks.add(totalCheck("Profit / loss vs report header",
                    batch.getRptProfit(), cur - inv));
        }
        if (batch.getRptInvested() == 0 && batch.getRptCurrent() == 0) {
            checks.add(check("Report header totals", "INFO",
                    "-", "-", "This statement does not print header totals - review the rows below"));
        }
        checks.add(check("Rows parsed", "INFO",
                String.valueOf(batch.getRptHoldingsCount() + batch.getRptTxnCount()),
                String.valueOf(lines.size()),
                "Holdings rows: " + holding + ", transaction rows: " + txnLines));
        checks.add(check("Units total", "INFO", "-", MfUtil.roundUnits(units) + "",
                "Sum of units across all holding rows"));
        checks.add(check("Duplicates (no double entry)", "PASS",
                "0 duplicates created",
                nExists + " row(s) already known",
                nExists > 0
                        ? "Existing rows are marked grey and will be skipped on accept (MF.md 22)"
                        : "No existing entries found - nothing to duplicate"));
        if (nNewScheme > 0) {
            checks.add(check("Unregistered schemes", "WARN",
                    "0", String.valueOf(nNewScheme),
                    nNewScheme + " row(s) use schemes not in the registered list - remap them, "
                            + "or they will be registered on accept (MF.md 20)"));
        }
        if (nSkipped > 0) {
            checks.add(check("Empty rows", "INFO",
                    "0", String.valueOf(nSkipped),
                    nSkipped + " row(s) with no units or amount (empty holdings / service notes) "
                            + "- ignored on accept, nothing to import"));
        }
        if (MfConstants.FILE_PDF.equals(batch.getFileType())) {
            checks.add(check("Cost basis", "INFO",
                    "report Investment column",
                    batch.getRptInvested() > 0 ? String.valueOf(batch.getRptInvested()) : "not printed",
                    "PDF statements print units and market value but no cost basis - invested value of a "
                            + "new holding is estimated from its transaction history; an xlsx import "
                            + "replaces it with the exact figure"));
        }
        if (nError > 0) {
            checks.add(check("Unreadable rows", "FAIL",
                    "0", String.valueOf(nError),
                    nError + " row(s) could not be read - check them in the row list"));
        }
        if (batch.getReportPan() != null && !batch.getReportPan().isBlank()
                && !batch.getReportPan().equalsIgnoreCase(pan)) {
            checks.add(check("Statement PAN", "WARN", pan, batch.getReportPan(),
                    "Statement belongs to a different PAN - rows will be filed under your login PAN"));
        }

        boolean fail = checks.stream().anyMatch(c -> "FAIL".equals(c.get("severity")));
        boolean warn = checks.stream().anyMatch(c -> "WARN".equals(c.get("severity")));
        batch.setValidStatus(fail ? MfConstants.VALID_RED : warn ? MfConstants.VALID_AMBER : MfConstants.VALID_GREEN);
        try {
            batch.setValidationDetails(mapper.writeValueAsString(checks));
        } catch (Exception e) {
            batch.setValidationDetails("[]");
        }
        batchRepository.save(batch);

        return buildResponse(batch, lines, pan);
    }

    // ------------------------------------------------------------------ remap

    @Override
    @Transactional
    public Map<String, Object> remap(Long id, Long lineId, Long schemeId, String pan) {
        MfImportBatch batch = loadBatch(id, pan);
        if (!MfConstants.BATCH_UPLOADED.equals(batch.getStatus())) {
            throw new RuntimeException("This batch has already been processed");
        }
        MfImportLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new RuntimeException("Import line not found"));
        if (!batch.getId().equals(line.getBatchId())) {
            throw new RuntimeException("Import line does not belong to this batch");
        }
        MfScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new RuntimeException("Scheme not found"));
        line.setTargetSchemeId(schemeId);
        line.setStatus(MfConstants.LINE_MAPPED);
        line.setMessage("Remapped to " + scheme.getSchemeName() + " (" + scheme.getAmcName() + ")");
        lineRepository.save(line);
        return validate(id, pan);
    }

    // ----------------------------------------------------------------- accept

    @Override
    @Transactional
    public Map<String, Object> accept(Long id, String pan) {
        MfImportBatch batch = loadBatch(id, pan);
        if (!MfConstants.BATCH_UPLOADED.equals(batch.getStatus())) {
            throw new RuntimeException("This batch has already been processed");
        }
        List<MfImportLine> lines = lineRepository.findByBatchIdOrderByLineNoAsc(id);

        Set<Long> newSchemes = new HashSet<>();
        Set<Long> headersWithHolding = new HashSet<>();
        Map<Long, double[]> txnAgg = new HashMap<>(); // headerId -> [units, amount]
        int createdTxns = 0, skipped = 0, createdHeaders = 0, updatedHeaders = 0, acceptedLines = 0;

        // A batch that carries a holdings section (CAS statement) is a position
        // snapshot: the holdings rows define what is held today and the transaction
        // rows are history for the transaction table. A scheme with transactions but
        // no holding row is an exited position - netting its (partial) history into
        // the header would show phantom negative units/value. Only a pure transaction
        // export (no holdings section) aggregates into headers.
        boolean snapshotBatch = lines.stream()
                .anyMatch(l -> MfConstants.LINE_HOLDING.equals(l.getLineType()));

        // 1) transactions first - their aggregate effect is applied later, only
        //    for headers the statement's holding rows do not snapshot
        for (MfImportLine l : lines) {
            if (!MfConstants.LINE_TRANSACTION.equals(l.getLineType())) continue;
            if (MfConstants.LINE_ERROR.equals(l.getStatus())
                    || MfConstants.LINE_SKIPPED.equals(l.getStatus())
                    || MfConstants.LINE_EXISTS.equals(l.getStatus())) continue;
            if (l.getTxnDate() == null) {
                l.setStatus(MfConstants.LINE_ERROR);
                l.setMessage("Missing date - skipped");
                continue;
            }
            MfScheme scheme = resolveScheme(l, newSchemes, pan);
            MfHeader header = locateHeader(pan, scheme, l.getFolioNo(), l.getMatchedHeaderId());
            if (header == null) {
                header = createHeader(pan, scheme, l.getFolioNo());
                createdHeaders++;
            }
            if (transactionRepository.countByDedupeKey(l.getDedupeKey()) > 0
                    || looseExists(header, l) || crossFolioExists(pan, scheme, l)) {
                l.setStatus(MfConstants.LINE_EXISTS);
                l.setMatchedHeaderId(header.getId());
                l.setMessage("Already recorded - duplicate skipped");
                skipped++;
                continue;
            }
            MfTransaction t = new MfTransaction();
            t.setHeaderId(header.getId());
            t.setPan(pan);
            t.setTxnDate(l.getTxnDate());
            t.setTxnType(l.getTxnType() == null || l.getTxnType().isBlank()
                    ? inferTxnType(l.getDescription(), l.getUnits(), l.getAmount()) : l.getTxnType());
            t.setDescription(l.getDescription());
            t.setUnits(l.getUnits());
            t.setNav(l.getNav());
            t.setAmount(l.getAmount());
            t.setSource(MfConstants.SRC_IMPORT);
            t.setDedupeKey(l.getDedupeKey());
            transactionRepository.save(t);

            double[] a = txnAgg.computeIfAbsent(header.getId(), k -> new double[2]);
            a[0] += l.getUnits();
            a[1] += l.getAmount();
            l.setMatchedHeaderId(header.getId());
            l.setMessage("Imported on accept");
            createdTxns++;
            acceptedLines++;
        }

        // 2) holdings - latest statement snapshot wins, never a duplicate header (MF.md 22)
        // A PDF statement prints units and market value but no cost basis, so its
        // invested value is an estimate from the in-window cash flows: it must never
        // overwrite an invested figure the system already holds (xlsx/csv carry the
        // report's exact "Investment" column).
        boolean pdfSourced = MfConstants.FILE_PDF.equals(batch.getFileType());
        for (MfImportLine l : lines) {
            if (!MfConstants.LINE_HOLDING.equals(l.getLineType())) continue;
            if (MfConstants.LINE_ERROR.equals(l.getStatus())
                    || MfConstants.LINE_SKIPPED.equals(l.getStatus())) continue;
            MfScheme scheme = resolveScheme(l, newSchemes, pan);
            MfHeader header = locateHeader(pan, scheme, l.getFolioNo(), l.getMatchedHeaderId());
            if (header == null) {
                header = createHeader(pan, scheme, l.getFolioNo());
                createdHeaders++;
            } else {
                updatedHeaders++;
            }
            header.setUnits(l.getUnits());
            if (!pdfSourced || header.getInvestedValue() <= 0) {
                header.setInvestedValue(l.getInvestedValue());
            }
            header.setCurrentValue(l.getCurrentValue());
            if (l.getUnits() > 0) {
                header.setNav(MfUtil.round(l.getCurrentValue() / l.getUnits(), 4));
                if (MfConstants.STATUS_CLOSED.equals(header.getStatus())) {
                    header.setStatus(MfConstants.STATUS_ACTIVE);
                }
            }
            header.setUpdatedAt(LocalDateTime.now());
            headerRepository.save(header);
            headersWithHolding.add(header.getId());
            l.setMatchedHeaderId(header.getId());
            l.setMessage("Applied on accept");
            acceptedLines++;
        }

        // 3) transaction-only headers
        if (snapshotBatch) {
            // CAS-style batch: a scheme without a holding row is exited - keep the
            // transactions for the history report but hold the position flat at zero
            // instead of inheriting a negative net from partial transaction history.
            for (Long hid : txnAgg.keySet()) {
                if (headersWithHolding.contains(hid)) continue;
                MfHeader h = headerRepository.findById(hid).orElse(null);
                if (h == null) continue;
                h.setUnits(0);
                h.setInvestedValue(0);
                h.setCurrentValue(0);
                h.setStatus(MfConstants.STATUS_CLOSED);
                h.setUpdatedAt(LocalDateTime.now());
                headerRepository.save(h);
            }
        } else {
            // Pure transaction export (no holdings section): the transaction
            // aggregate is the only source of the position, so apply it additively.
            for (Map.Entry<Long, double[]> e : txnAgg.entrySet()) {
                if (headersWithHolding.contains(e.getKey())) continue;
                MfHeader h = headerRepository.findById(e.getKey()).orElse(null);
                if (h == null) continue;
                h.setUnits(MfUtil.roundUnits(h.getUnits() + e.getValue()[0]));
                h.setInvestedValue(MfUtil.round(h.getInvestedValue() + e.getValue()[1], 2));
                if (h.getNav() > 0) {
                    h.setCurrentValue(MfUtil.round(h.getUnits() * h.getNav(), 2));
                } else {
                    h.setCurrentValue(MfUtil.round(h.getCurrentValue() + e.getValue()[1], 2));
                }
                h.setUpdatedAt(LocalDateTime.now());
                headerRepository.save(h);
            }
        }

        batch.setStatus(MfConstants.BATCH_ACCEPTED);
        batch.setAcceptedAt(LocalDateTime.now());
        batchRepository.save(batch);
        lineRepository.saveAll(lines);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("acceptedLines", acceptedLines);
        out.put("createdTransactions", createdTxns);
        out.put("skippedDuplicates", skipped);
        out.put("createdHeaders", createdHeaders);
        out.put("updatedHeaders", updatedHeaders);
        out.put("registeredSchemes", newSchemes.size());
        return out;
    }

    // --------------------------------------------------------------- internals

    /** Classifies one line: NEW / EXISTS / NEW_SCHEME / ERROR (MF.md 15/20/22). */
    private void classify(String pan, MfImportLine l, Set<String> seenInReport) {
        if (l.getSchemeName() == null || l.getSchemeName().isBlank()) {
            l.setStatus(MfConstants.LINE_ERROR);
            l.setMessage("Row has no scheme name");
            return;
        }
        if (MfConstants.LINE_HOLDING.equals(l.getLineType())) {
            if (l.getUnits() <= 0 && l.getInvestedValue() <= 0 && l.getCurrentValue() <= 0) {
                // A row the report itself prints with no units and no values is not an
                // unreadable row - it simply carries nothing to import, so it must not
                // fail the reconciliation colour when the totals match (MF.md 15).
                l.setStatus(MfConstants.LINE_SKIPPED);
                l.setMessage("Empty row in the report (no units or values) - nothing to import");
                return;
            }
        } else if (l.getTxnDate() == null) {
            l.setStatus(MfConstants.LINE_ERROR);
            l.setMessage("Row has no readable date");
            return;
        }
        if (MfConstants.LINE_TRANSACTION.equals(l.getLineType())
                && l.getUnits() == 0 && l.getAmount() == 0) {
            // CAS statements list service notes (nominee registration, address update,
            // SIP registered ...) in the transaction log - they carry nothing to book
            l.setStatus(MfConstants.LINE_SKIPPED);
            l.setMessage("Activity row with no units or amount - nothing to import");
            return;
        }

        if (!seenInReport.add(l.getDedupeKey())) {
            l.setStatus(MfConstants.LINE_EXISTS);
            l.setMatchedHeaderId(null);
            l.setMessage("Duplicate row inside this report - skipped");
            return;
        }

        MfScheme scheme = findScheme(l.getSchemeName(), l.getAmcName());
        if (scheme == null) {
            l.setStatus(MfConstants.LINE_NEW_SCHEME);
            l.setMatchedHeaderId(null);
            l.setMessage("Scheme is not registered - remap it, or it will be registered on accept (MF.md 20)");
            return;
        }
        MfHeader header = locateHeader(pan, scheme, l.getFolioNo(), null);
        l.setMatchedHeaderId(header == null ? null : header.getId());

        if (MfConstants.LINE_HOLDING.equals(l.getLineType())) {
            if (header != null) {
                l.setStatus(MfConstants.LINE_EXISTS);
                l.setMessage("Existing holding - values will be refreshed, no duplicate header is created");
            } else {
                l.setStatus(MfConstants.LINE_NEW);
                l.setMessage("New holding - ready to accept");
            }
            return;
        }

        // transaction: already in backend? (MF.md 22)
        if (transactionRepository.countByDedupeKey(l.getDedupeKey()) > 0) {
            l.setStatus(MfConstants.LINE_EXISTS);
            l.setMessage("This transaction already exists in the system - duplicate skipped");
            return;
        }
        if (header != null && looseExists(header, l)) {
            l.setStatus(MfConstants.LINE_EXISTS);
            l.setMessage("Same transaction already recorded on this holding - duplicate skipped");
            return;
        }
        // Cross-folio fallback (MF.md 22): xlsx/csv transaction sheets carry no folio
        // column while the PDF prints folio sections, so the same transaction can be
        // attributed to different folios of one scheme depending on the upload format.
        if (crossFolioExists(pan, scheme, l)) {
            l.setStatus(MfConstants.LINE_EXISTS);
            l.setMessage("Same transaction already recorded on another folio of this scheme - duplicate skipped");
            return;
        }
        l.setStatus(MfConstants.LINE_NEW);
        l.setMessage(header == null ? "New transaction - a holding will be created" : "New transaction - ready to accept");
    }

    /**
     * Same transaction on another folio of the same scheme (MF.md 22): matches on
     * date + type + units + amount with the same tolerance as {@link #looseExists},
     * ignoring the folio because that is exactly what the two upload formats
     * disagree about. Holdings totals are unaffected either way (the holding rows
     * snapshot each header), but the transaction table must never book it twice.
     */
    private boolean crossFolioExists(String pan, MfScheme scheme, MfImportLine l) {
        if (l.getTxnDate() == null || scheme == null) return false;
        String type = l.getTxnType() == null || l.getTxnType().isBlank()
                ? inferTxnType(l.getDescription(), l.getUnits(), l.getAmount()) : l.getTxnType();
        for (MfHeader h : headerRepository.findByPanAndSchemeId(pan, scheme.getId())) {
            for (MfTransaction t : transactionRepository.findByHeaderIdOrderByTxnDateAscIdAsc(h.getId())) {
                if (l.getTxnDate().equals(t.getTxnDate())
                        && type.equals(t.getTxnType())
                        && Math.abs(t.getUnits() - l.getUnits()) < 0.01
                        && Math.abs(t.getAmount() - l.getAmount()) < 0.5) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Loose duplicate check (MF.md 22): SIP instalments auto-executed locally
     * may differ a hair in units from the statement's rounding, so compare
     * date + type + near-equal units and amount on the same holding.
     */
    private boolean looseExists(MfHeader header, MfImportLine l) {
        if (l.getTxnDate() == null) return false;
        String type = l.getTxnType() == null || l.getTxnType().isBlank()
                ? inferTxnType(l.getDescription(), l.getUnits(), l.getAmount()) : l.getTxnType();
        for (MfTransaction t : transactionRepository.findByHeaderIdOrderByTxnDateAscIdAsc(header.getId())) {
            if (l.getTxnDate().equals(t.getTxnDate())
                    && type.equals(t.getTxnType())
                    && Math.abs(t.getUnits() - l.getUnits()) < 0.01
                    && Math.abs(t.getAmount() - l.getAmount()) < 0.5) {
                return true;
            }
        }
        return false;
    }

    private MfScheme findScheme(String name, String amc) {
        String n = name == null ? "" : name.trim();
        String a = amc == null ? "" : amc.trim();
        List<MfScheme> exact = schemeRepository.findBySchemeNameIgnoreCaseAndAmcNameIgnoreCase(n, a);
        MfScheme s = exact.isEmpty() ? null : exact.get(0);
        if (s != null) return s;
        List<MfScheme> byName = schemeRepository.findBySchemeNameIgnoreCase(n);
        if (byName.size() == 1) return byName.get(0);
        if (!a.isEmpty()) {
            for (MfScheme c : byName) {
                String ca = c.getAmcName() == null ? "" : c.getAmcName().toLowerCase(Locale.ROOT);
                if (ca.contains(a.toLowerCase(Locale.ROOT))
                        || a.toLowerCase(Locale.ROOT).contains(ca)) {
                    return c;
                }
            }
        }
        // multiple matches with no usable AMC need an explicit remap (MF.md 20)
        return null;
    }

    /** Resolve for accept: exact -> single-name -> remap -> register (MF.md 20). */
    private MfScheme resolveScheme(MfImportLine l, Set<Long> created, String pan) {
        if (l.getTargetSchemeId() != null) {
            return schemeRepository.findById(l.getTargetSchemeId())
                    .orElseThrow(() -> new RuntimeException("Remapped scheme no longer exists"));
        }
        MfScheme s = findScheme(l.getSchemeName(), l.getAmcName());
        if (s != null) return s;
        if (l.getSchemeName() == null || l.getSchemeName().isBlank()) {
            throw new RuntimeException("A row has no scheme name and cannot be accepted");
        }
        List<MfScheme> ambiguous = schemeRepository.findBySchemeNameIgnoreCase(l.getSchemeName());
        if (!ambiguous.isEmpty()) {
            throw new RuntimeException("'" + l.getSchemeName() + "' matches several AMCs - "
                    + "remap the row to the correct scheme before accepting");
        }
        MfScheme fresh = new MfScheme();
        fresh.setSchemeName(l.getSchemeName().trim());
        fresh.setAmcName(l.getAmcName() == null ? "" : l.getAmcName().trim());
        fresh.setCategory(l.getCategory() == null ? "" : l.getCategory().trim());
        fresh = schemeRepository.save(fresh);
        created.add(fresh.getId());
        return fresh;
    }

    private MfHeader locateHeader(String pan, MfScheme scheme, String folio, Long hintId) {
        if (hintId != null) {
            MfHeader hinted = headerRepository.findById(hintId).orElse(null);
            if (hinted != null && pan.equalsIgnoreCase(hinted.getPan())) return hinted;
        }
        if (folio != null && !folio.isBlank()) {
            List<MfHeader> found = headerRepository
                    .findByPanAndSchemeIdAndFolioNoIgnoreCase(pan, scheme.getId(), folio);
            return found.isEmpty() ? null : found.get(0);
        }
        List<MfHeader> all = headerRepository.findByPanAndSchemeId(pan, scheme.getId());
        return all.isEmpty() ? null : all.get(0);
    }

    private MfHeader createHeader(String pan, MfScheme scheme, String folio) {
        MfHeader h = new MfHeader();
        h.setPan(pan);
        // CONSISTENCY.md 6: accepted import rows mandatorily reference the client
        List<Client> owners = clientRepository.findByPan(pan);
        if (owners.isEmpty()) {
            throw new RuntimeException("Unknown client " + pan);
        }
        h.setClient(owners.get(0));
        h.setScheme(scheme);
        h.setFolioNo(folio == null ? "" : folio);
        h.setRegistrationType(MfConstants.REG_ONE_TIME);
        h.setStatus(MfConstants.STATUS_ACTIVE);
        h.setUpdatedAt(LocalDateTime.now());
        return headerRepository.save(h);
    }

    private MfImportBatch loadBatch(Long id, String pan) {
        return batchRepository.findByIdAndPan(id, pan).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Import batch not found with id " + id));
    }

    // ------------------------------------------------------------- reporting

    private Map<String, Object> buildResponse(MfImportBatch batch, List<MfImportLine> lines, String pan) {
        List<Map<String, Object>> checks = new ArrayList<>();
        try {
            if (batch.getValidationDetails() != null && !batch.getValidationDetails().isBlank()) {
                checks = mapper.readValue(batch.getValidationDetails(), List.class);
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch", batch);
        out.put("lines", lines);
        out.put("checks", checks);
        out.put("detail", buildDetail(batch, lines, pan));
        return out;
    }

    /**
     * Detail report for the reconciliation page (MF.md 23 / 25): totals before,
     * what the statement says, and totals projected after the user accepts.
     */
    private Map<String, Object> buildDetail(MfImportBatch batch, List<MfImportLine> lines, String pan) {
        List<MfHeader> all = headerRepository.search(pan, null, null, null, null, null, null);
        double sysInv = 0, sysCur = 0, sysUnits = 0;
        for (MfHeader h : all) {
            sysInv += h.getInvestedValue();
            sysCur += h.getCurrentValue();
            sysUnits += h.getUnits();
        }

        double repInv = 0, repCur = 0, repUnits = 0;
        int repHoldings = 0, repTxns = 0;
        for (MfImportLine l : lines) {
            if (MfConstants.LINE_HOLDING.equals(l.getLineType())) {
                repHoldings++;
                if (MfConstants.LINE_ERROR.equals(l.getStatus())
                        || MfConstants.LINE_SKIPPED.equals(l.getStatus())) continue;
                repInv += l.getInvestedValue();
                repCur += l.getCurrentValue();
                repUnits += l.getUnits();
            } else {
                repTxns++;
            }
        }

        // projected after accept: holdings snapshot replaces matched headers,
        // brand-new holdings add, and transactions add only where no holding
        // row covers the same scheme+folio
        Set<String> coveredKeys = new HashSet<>();
        Map<String, MfHeader> headerByKey = new HashMap<>();
        for (MfImportLine l : lines) {
            if (!MfConstants.LINE_HOLDING.equals(l.getLineType())) continue;
            if (MfConstants.LINE_ERROR.equals(l.getStatus())
                    || MfConstants.LINE_SKIPPED.equals(l.getStatus())) continue;
            coveredKeys.add(key(l.getSchemeName(), l.getFolioNo()));
            if (l.getMatchedHeaderId() != null) {
                headerRepository.findById(l.getMatchedHeaderId())
                        .ifPresent(h -> headerByKey.put(key(l.getSchemeName(), l.getFolioNo()), h));
            }
        }

        double projInv = sysInv, projCur = sysCur, projUnits = sysUnits;
        int newHeaders = 0, refreshHeaders = 0, newTxns = 0, dupSkipped = 0, schemesToRegister = 0;
        // mirrors accept(): only a pure transaction export adds transaction
        // aggregates to the projected position - see accept() step 3
        boolean snapshotBatch = lines.stream()
                .anyMatch(l -> MfConstants.LINE_HOLDING.equals(l.getLineType()));
        // once the batch is accepted the transaction rows are already written -
        // projecting them again would double the "After accept" transaction count
        boolean acceptedBatch = MfConstants.BATCH_ACCEPTED.equals(batch.getStatus());
        boolean pdfSourced = MfConstants.FILE_PDF.equals(batch.getFileType());
        for (MfImportLine l : lines) {
            if (MfConstants.LINE_ERROR.equals(l.getStatus())
                    || MfConstants.LINE_SKIPPED.equals(l.getStatus())) continue;
            // Only transaction duplicates are skipped rows: an EXISTING holding is
            // a refresh (its values are replaced), so it must still project forward
            if (MfConstants.LINE_EXISTS.equals(l.getStatus())
                    && MfConstants.LINE_TRANSACTION.equals(l.getLineType())) {
                dupSkipped++;
                continue;
            }
            if (l.getTargetSchemeId() == null
                    && MfConstants.LINE_NEW_SCHEME.equals(l.getStatus())) {
                schemesToRegister++;
            }
            if (MfConstants.LINE_HOLDING.equals(l.getLineType())) {
                MfHeader match = headerByKey.get(key(l.getSchemeName(), l.getFolioNo()));
                if (match != null) {
                    // mirrors accept(): a PDF refresh keeps the invested figure the
                    // system already holds (PDF statements carry no cost basis)
                    if (!pdfSourced || match.getInvestedValue() <= 0) {
                        projInv += l.getInvestedValue() - match.getInvestedValue();
                    }
                    projCur += l.getCurrentValue() - match.getCurrentValue();
                    projUnits += l.getUnits() - match.getUnits();
                    refreshHeaders++;
                } else {
                    projInv += l.getInvestedValue();
                    projCur += l.getCurrentValue();
                    projUnits += l.getUnits();
                    newHeaders++;
                }
            } else {
                if (acceptedBatch) continue;
                newTxns++;
                if (!snapshotBatch && !coveredKeys.contains(key(l.getSchemeName(), l.getFolioNo()))) {
                    projInv += l.getAmount();
                    projUnits += l.getUnits();
                }
            }
        }

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("headers", all.size());
        system.put("transactions", transactionRepository.countByPan(pan));
        system.put("invested", MfUtil.round(sysInv, 2));
        system.put("current", MfUtil.round(sysCur, 2));
        system.put("profit", MfUtil.round(sysCur - sysInv, 2));
        system.put("units", MfUtil.roundUnits(sysUnits));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("fileName", batch.getFileName());
        report.put("fileType", batch.getFileType());
        report.put("clientName", batch.getClientName());
        report.put("reportPan", batch.getReportPan());
        report.put("from", batch.getReportFrom());
        report.put("to", batch.getReportTo());
        report.put("invested", MfUtil.round(batch.getRptInvested(), 2));
        report.put("current", MfUtil.round(batch.getRptCurrent(), 2));
        report.put("profit", MfUtil.round(batch.getRptProfit(), 2));
        report.put("holdings", repHoldings);
        report.put("transactions", repTxns);
        report.put("parsedInvested", MfUtil.round(repInv, 2));
        report.put("parsedCurrent", MfUtil.round(repCur, 2));
        report.put("parsedUnits", MfUtil.roundUnits(repUnits));

        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("headers", all.size() + newHeaders);
        projected.put("refreshedHeaders", refreshHeaders);
        projected.put("transactions", transactionRepository.countByPan(pan) + newTxns);
        projected.put("invested", MfUtil.round(projInv, 2));
        projected.put("current", MfUtil.round(projCur, 2));
        projected.put("profit", MfUtil.round(projCur - projInv, 2));
        projected.put("units", MfUtil.roundUnits(projUnits));
        projected.put("duplicateRowsSkipped", dupSkipped);
        projected.put("schemesToRegister", schemesToRegister);

        Map<String, Object> counts = new LinkedHashMap<>();
        for (MfImportLine l : lines) {
            String st = l.getStatus() == null ? "?" : l.getStatus();
            counts.merge(st, 1, (a, b) -> ((Integer) a) + ((Integer) b));
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("system", system);
        detail.put("report", report);
        detail.put("projected", projected);
        detail.put("counts", counts);
        return detail;
    }

    private static String key(String scheme, String folio) {
        return ((scheme == null ? "" : scheme.trim().toLowerCase(Locale.ROOT)) + "|"
                + (folio == null ? "" : folio.trim().toLowerCase(Locale.ROOT)));
    }

    // ----------------------------------------------------------------- checks

    private Map<String, Object> totalCheck(String name, double expected, double actual) {
        double tol = Math.max(1.0, Math.abs(expected) * TOTAL_TOL);
        boolean pass = Math.abs(expected - actual) <= tol;
        return check(name, pass ? "PASS" : "FAIL",
                MfUtil.round(expected, 2) + "", MfUtil.round(actual, 2) + "",
                pass ? "Matches the report total"
                        : String.format("Differs from the report total by %.2f", actual - expected));
    }

    private Map<String, Object> check(String name, String severity, String expected,
                                       String actual, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("severity", severity);
        m.put("expected", expected);
        m.put("actual", actual);
        m.put("message", message);
        return m;
    }

    // ----------------------------------------------------------------- shared

    /** Description -> transaction type (deterministic, so dedupe keys agree). */
    static String inferTxnType(String description, double units, double amount) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (d.contains("systematic") || d.contains(" sip ") || d.startsWith("sip")
                || d.contains("(sip")) return MfConstants.TXN_SIP_INSTALMENT;
        if (d.contains("switch out")) return MfConstants.TXN_SWITCH_OUT;
        if (d.contains("switch in")) return MfConstants.TXN_SWITCH_IN;
        if (d.contains("redemption") || d.contains("withdrawal") || d.contains("reversal"))
            return MfConstants.TXN_REDEMPTION;
        if (d.contains("dividend") || d.contains("bonus")) return MfConstants.TXN_DIVIDEND;
        if (d.contains("purchase") || d.contains("subscription") || d.contains("additional"))
            return MfConstants.TXN_PURCHASE;
        if (units < 0 || amount < 0) return MfConstants.TXN_REDEMPTION;
        if (units > 0 || amount > 0) return MfConstants.TXN_PURCHASE;
        return MfConstants.TXN_OTHER;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
