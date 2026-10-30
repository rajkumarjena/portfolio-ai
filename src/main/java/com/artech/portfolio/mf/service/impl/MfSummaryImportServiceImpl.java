package com.artech.portfolio.mf.service.impl;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientEmail;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.mf.entity.MfAmc;
import com.artech.portfolio.mf.entity.MfFolio;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import com.artech.portfolio.mf.entity.MfSummaryReconciliationLine;
import com.artech.portfolio.mf.parser.MfSummaryParser;
import com.artech.portfolio.mf.parser.MfSummaryReport;
import com.artech.portfolio.mf.repository.MfAmcRepository;
import com.artech.portfolio.mf.repository.MfFolioRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeValuationRepository;
import com.artech.portfolio.mf.repository.MfImportBatchRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import com.artech.portfolio.mf.repository.MfSummaryReconciliationRepository;
import com.artech.portfolio.mf.service.MfSummaryImportService;
import com.artech.portfolio.mf.service.MfAmcResolver;
import com.artech.portfolio.mf.service.MfFolioMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class MfSummaryImportServiceImpl implements MfSummaryImportService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal TOLERANCE_PERCENT = new BigDecimal("0.01");
    private final MfSummaryParser parser = new MfSummaryParser();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClientRepository clients;
    private final MfImportBatchRepository batches;
    private final MfFolioSchemeTransactionRepository transactions;
    private final MfFolioRepository folios;
    private final MfFolioSchemeRepository schemes;
    private final MfFolioSchemeValuationRepository valuations;
    private final MfPortfolioSummaryRepository summaries;
    private final MfSummaryReconciliationRepository reviewRows;
    private final MfAmcResolver amcResolver;

    public MfSummaryImportServiceImpl(ClientRepository clients, MfImportBatchRepository batches,
            MfFolioSchemeTransactionRepository transactions, MfFolioRepository folios,
            MfFolioSchemeRepository schemes, MfFolioSchemeValuationRepository valuations,
            MfPortfolioSummaryRepository summaries,
            MfSummaryReconciliationRepository reviewRows, MfAmcResolver amcResolver) {
        this.clients = clients;
        this.batches = batches;
        this.transactions = transactions;
        this.folios = folios;
        this.schemes = schemes;
        this.valuations = valuations;
        this.summaries = summaries;
        this.reviewRows = reviewRows;
        this.amcResolver = amcResolver;
    }

    @Override
    @Transactional
    public Map<String, Object> upload(String ownerPan, String fileName, byte[] data, String password) {
        MfSummaryReport report = parser.parse(data, password);
        Client client = clients.findByPanIgnoreCase(ownerPan).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The signed-in client could not be found."));
        boolean emailMatches = client.getEmails().stream().map(ClientEmail::getEmail)
                .filter(Objects::nonNull).map(MfSummaryImportServiceImpl::emailKey)
                .anyMatch(report.email::equals);
        if (!emailMatches) {
            throw new IllegalArgumentException("Invalid client's report: summary Email Id does not match any email address registered to this account.");
        }

        List<Long> storedSchemeIds = schemes.findByOwnerPan(ownerPan).stream().map(MfFolioScheme::getId).toList();
        LocalDate latestDetail = java.util.stream.Stream.concat(
                        transactions.findByOwnerPan(ownerPan).stream().map(MfFolioSchemeTransaction::getTxnDate),
                        storedSchemeIds.isEmpty() ? java.util.stream.Stream.empty()
                                : valuations.findBySchemeIdIn(storedSchemeIds).stream().map(MfFolioSchemeValuation::getValueDate))
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        if (latestDetail != null && !report.asOn.isAfter(latestDetail)) {
            throw new IllegalArgumentException("Summary statement is dated " + report.asOn
                    + ", but the detailed statement already contains transactions through " + latestDetail
                    + ". The detailed statement is newer, so this summary was not imported.");
        }

        MfImportBatch batch = new MfImportBatch();
        batch.setPan(ownerPan);
        batch.setFileName(fileName);
        batch.setFileType(MfSummaryReport.FILE_TYPE);
        batch.setStatementKind("SUMMARY");
        batch.setReportTo(report.asOn);
        batch.setClientName(client.getName());
        batch.setTxnCount(0);
        batch.setFolioCount((int) report.lines.stream().map(line -> key(line.folio)).distinct().count());
        batch.setSchemeCount(report.lines.size());
        try {
            batch.setParsedJson(mapper.writeValueAsString(report));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to store the parsed summary report.", e);
        }
        batches.save(batch);

        reviewRows.deleteByBatch(batch.getId(), ownerPan);
        Map<String, MfFolio> folioIndex = new HashMap<>();
        Map<Long, MfFolio> foliosById = new HashMap<>();
        List<MfFolio> existingFolios = folios.findByOwnerPanOrderByFolioNo(ownerPan);
        for (MfFolio folio : existingFolios) {
            folioIndex.put(key(folio.getFolioNo()), folio);
            foliosById.put(folio.getId(), folio);
        }
        Map<String, MfFolio> amcByIsin = new HashMap<>();
        java.util.Set<String> ambiguousIsins = new java.util.HashSet<>();
        for (MfFolioScheme scheme : schemes.findByOwnerPan(ownerPan)) {
            if (scheme.getIsin() == null || scheme.getIsin().isBlank()) continue;
            MfFolio parent = foliosById.get(scheme.getFolioId());
            if (parent != null && parent.getAmc() != null) {
                String normalizedIsin = scheme.getIsin().toUpperCase(Locale.ROOT);
                if (ambiguousIsins.contains(normalizedIsin)) continue;
                MfFolio previous = amcByIsin.putIfAbsent(normalizedIsin, parent);
                if (previous != null && !Objects.equals(previous.getAmcId(), parent.getAmcId())) {
                    amcByIsin.remove(normalizedIsin);
                    ambiguousIsins.add(normalizedIsin);
                }
            }
        }
        List<MfSummaryReconciliationLine> staged = new ArrayList<>(report.lines.size());
        for (MfSummaryReport.Line line : report.lines) {
            MfSummaryReconciliationLine row = new MfSummaryReconciliationLine();
            row.setBatchId(batch.getId());
            row.setOwnerPan(ownerPan);
            row.setFolioNo(line.folio);
            MfFolio knownFolio = folioIndex.get(key(line.folio));
            boolean probableMatch = false;
            if (knownFolio == null) {
                knownFolio = findRelatedFolio(line.folio, existingFolios);
                probableMatch = knownFolio != null;
            }
            String isinKey = line.isin == null ? "" : line.isin.toUpperCase(Locale.ROOT);
            MfFolio isinFolio = ambiguousIsins.contains(isinKey) ? null : amcByIsin.get(isinKey);
            MfFolio amcSource = knownFolio != null ? knownFolio : isinFolio;
            String amc = amcSource == null ? null : amcSource.getAmc();
            row.setAmc(amc);
            row.setAmcId(amcSource == null ? null : amcSource.getAmcId());
            row.setResolvedFolioId(knownFolio == null ? null : knownFolio.getId());
            row.setProbableFolioMatch(probableMatch);
            row.setScheme(line.scheme);
            row.setIsin(line.isin);
            row.setCostValue(line.cost);
            row.setUnits(line.units);
            row.setNavDate(line.navDate);
            row.setPrice(line.price);
            row.setMarketValue(line.market);
            row.setRegistrar(line.registrar);
            boolean provisional = probableMatch || (knownFolio == null && isinFolio != null);
            row.setMatchStatus(amc == null || provisional ? "AMBER" : "GREEN");
            row.setMessage(probableMatch
                    ? "Probable folio match: " + knownFolio.getFolioNo() + "; verify the AMC before accepting."
                    : (amc == null ? "AMC could not be derived; edit the AMC before accepting."
                    : (knownFolio == null ? "AMC resolved by ISIN; verify the folio relationship."
                    : "AMC resolved from existing folio data.")));
            staged.add(row);
        }
        reviewRows.saveAll(staged);
        Totals sums = total(staged);
        boolean green = within(report.totalCost, sums.cost) && within(report.totalMarket, sums.market)
                && !staged.isEmpty() && staged.stream().allMatch(row -> "GREEN".equals(row.getMatchStatus()));
        batch.setValidStatus(green ? "GREEN" : "AMBER");
        batches.save(batch);
        return Map.of("batch", batch, "status", batch.getValidStatus(), "lineCount", staged.size(), "asOn", report.asOn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MfImportBatch> list(String ownerPan) {
        return batches.findByPanOrderByUploadedAtDesc(ownerPan).stream()
                .filter(batch -> "SUMMARY".equals(batch.getStatementKind())).toList();
    }

    @Override
    @Transactional
    public void discard(Long batchId, String ownerPan) {
        MfImportBatch batch = loadSummaryBatch(batchId, ownerPan);
        if ("ACCEPTED".equals(batch.getStatus())) {
            throw new IllegalArgumentException("An accepted summary cannot be discarded.");
        }
        reviewRows.deleteByBatch(batchId, ownerPan);
        batch.setStatus("DISCARDED");
        batch.setValidStatus("PENDING");
        batches.save(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> report(Long batchId, String ownerPan) {
        MfImportBatch batch = loadSummaryBatch(batchId, ownerPan);
        MfSummaryReport parsed = readBatch(batch);
        List<MfSummaryReconciliationLine> rows = reviewRows.findByBatchIdAndOwnerPanOrderById(batchId, ownerPan);
        Totals totals = total(rows);
        boolean green = within(parsed.totalCost, totals.cost) && within(parsed.totalMarket, totals.market)
                && !rows.isEmpty() && rows.stream().allMatch(r -> "GREEN".equals(r.getMatchStatus()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("asOn", parsed.asOn);
        result.put("email", parsed.email);
        result.put("rows", rows);
        result.put("expectedCost", parsed.totalCost);
        result.put("actualCost", totals.cost);
        result.put("expectedMarket", parsed.totalMarket);
        result.put("actualMarket", totals.market);
        result.put("costDifference", totals.cost.subtract(parsed.totalCost));
        result.put("marketDifference", totals.market.subtract(parsed.totalMarket));
        result.put("tolerancePercent", TOLERANCE_PERCENT);
        result.put("status", green ? "GREEN" : "AMBER");
        result.put("note", green ? "Summary totals match within ±0.01%."
                : "Review unresolved AMC labels and totals; differences are outside ±0.01%.");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> accept(Long batchId, String ownerPan, Map<Long, Map<String, String>> corrections) {
        MfImportBatch batch = loadSummaryBatch(batchId, ownerPan);
        MfSummaryReport parsed = readBatch(batch);
        List<MfSummaryReconciliationLine> rows = reviewRows.findByBatchIdAndOwnerPanOrderById(batchId, ownerPan);
        Map<Long, Map<String, String>> edits = corrections == null ? Map.of() : corrections;
        for (MfSummaryReconciliationLine row : rows) {
            Map<String, String> edit = edits.get(row.getId());
            if (edit != null) {
                String name = trimToNull(edit.get("amc"));
                String schemeName = trimToNull(edit.get("scheme"));
                if (name != null) row.setAmc(name);
                if (schemeName != null) row.setScheme(schemeName);
            }
            if (row.getAmc() == null || row.getAmc().isBlank() || row.getScheme() == null || row.getScheme().isBlank()) {
                throw new IllegalArgumentException("Every summary row must have an AMC and scheme name before it can be accepted.");
            }
        }
        Totals sums = total(rows);
        if (rows.isEmpty() || !within(parsed.totalCost, sums.cost) || !within(parsed.totalMarket, sums.market)) {
            throw new IllegalArgumentException("Summary totals do not match the PDF within ±0.01%; correct the row data before accepting.");
        }

        Map<String, MfFolio> folioIndex = new HashMap<>();
        Map<Long, MfFolio> foliosById = new HashMap<>();
        for (MfFolio folio : folios.findByOwnerPanOrderByFolioNo(ownerPan)) {
            folioIndex.put(key(folio.getFolioNo()), folio);
            foliosById.put(folio.getId(), folio);
        }
        Map<Long, String> selectedAmcByFolio = new HashMap<>();
        for (MfSummaryReconciliationLine row : rows) {
            MfFolio matched = row.getResolvedFolioId() == null ? folioIndex.get(key(row.getFolioNo()))
                    : foliosById.get(row.getResolvedFolioId());
            if (matched == null) continue;
            String normalizedAmc = normalize(row.getAmc());
            String previous = selectedAmcByFolio.putIfAbsent(matched.getId(), normalizedAmc);
            if (previous != null && !previous.equals(normalizedAmc)) {
                throw new IllegalArgumentException("All schemes in folio " + matched.getFolioNo()
                        + " must use the same AMC. Correct the AMC values before accepting.");
            }
        }
        List<MfFolioScheme> existingSchemes = schemes.findByOwnerPan(ownerPan);
        Map<String, MfFolioScheme> schemeIndex = new HashMap<>();
        Map<Long, MfFolioSchemeValuation> valuationIndex = new HashMap<>();
        if (!existingSchemes.isEmpty()) {
            for (MfFolioSchemeValuation valuation : valuations.findBySchemeIdIn(
                    existingSchemes.stream().map(MfFolioScheme::getId).toList())) {
                valuationIndex.put(valuation.getSchemeId(), valuation);
            }
        }
        for (MfFolioScheme scheme : existingSchemes) {
            MfFolio parent = foliosById.get(scheme.getFolioId());
            if (parent != null) {
                schemeIndex.put(schemeKey(parent.getFolioNo(), scheme.getIsin(), scheme.getScheme()), scheme);
            }
        }
        for (MfSummaryReconciliationLine row : rows) {
            MfFolio folio = row.getResolvedFolioId() == null ? folioIndex.get(key(row.getFolioNo()))
                    : folios.findById(row.getResolvedFolioId()).orElse(null);
            MfAmc amc = findOrCreateAmc(row.getAmc());
            row.setAmcId(amc.getId());
            if (folio == null) {
                folio = new MfFolio();
                folio.setOwnerPan(ownerPan);
                folio.setFolioNo(row.getFolioNo());
                folio.setRefreshFrom("SUMMARY_STATEMENT");
                folio.setAmcId(amc.getId());
                folio.setInvestorName(clients.findByPan(ownerPan).stream().findFirst().map(Client::getName).orElse(null));
                folio.setFolioPan(ownerPan);
                folio = folios.save(folio);
                folioIndex.put(key(folio.getFolioNo()), folio);
            } else {
                // MF_V5 5: the reviewed AMC is authoritative. If the user
                // corrects a label, link this folio (and its other holdings)
                // to the normalized AMC row selected in reconciliation.
                folio.setAmcId(amc.getId());
                folio.setRefreshFrom("SUMMARY_STATEMENT");
                folios.save(folio);
                for (MfFolioScheme linkedScheme : schemes.findByFolioId(folio.getId())) {
                    linkedScheme.setRefreshFrom("SUMMARY_STATEMENT");
                    schemes.save(linkedScheme);
                }
            }

            String idx = schemeKey(folio.getFolioNo(), row.getIsin(), row.getScheme());
            MfFolioScheme scheme = schemeIndex.get(idx);
            if (scheme == null) {
                scheme = findSchemeByFolioAndIsin(folio.getId(), row.getIsin());
            }
            if (scheme == null) {
                scheme = new MfFolioScheme();
                scheme.setOwnerPan(ownerPan);
                scheme.setFolioId(folio.getId());
                scheme.setScheme(row.getScheme());
                scheme.setIsin(row.getIsin());
            } else {
                scheme.setScheme(row.getScheme());
                if (row.getIsin() != null) scheme.setIsin(row.getIsin());
            }
            scheme.setRefreshFrom("SUMMARY_STATEMENT");
            scheme.setCloseUnits(row.getUnits());
            scheme.setCloseCalculated(row.getUnits());
            scheme = schemes.save(scheme);
            schemeIndex.put(schemeKey(folio.getFolioNo(), row.getIsin(), row.getScheme()), scheme);

            MfFolioSchemeValuation valuation = valuationIndex.getOrDefault(scheme.getId(), new MfFolioSchemeValuation());
            valuation.setOwnerPan(ownerPan);
            valuation.setSchemeId(scheme.getId());
            valuation.setValueDate(row.getNavDate() == null ? parsed.asOn : row.getNavDate());
            valuation.setNav(row.getPrice());
            valuation.setCostValue(row.getCostValue());
            valuation.setMarketValue(row.getMarketValue());
            valuations.save(valuation);
            valuationIndex.put(scheme.getId(), valuation);
            folio.setAmcId(amc.getId());
            folio.setRefreshFrom("SUMMARY_STATEMENT");
            folios.save(folio);
            row.setAmcId(amc.getId());
            row.setProbableFolioMatch(false);
            row.setMatchStatus("GREEN");
            row.setMessage("Ready to accept");
            row.setAmc(amc.getName());
        }

        summaries.deleteByOwner(ownerPan);
        Map<Long, Totals> byAmc = new LinkedHashMap<>();
        for (MfSummaryReconciliationLine row : rows) {
            MfAmc amc = findOrCreateAmc(row.getAmc());
            row.setAmcId(amc.getId());
            Totals sum = byAmc.computeIfAbsent(amc.getId(), x -> new Totals());
            sum.cost = sum.cost.add(nvl(row.getCostValue()));
            sum.market = sum.market.add(nvl(row.getMarketValue()));
        }
        for (Map.Entry<Long, Totals> entry : byAmc.entrySet()) {
            MfPortfolioSummary summary = new MfPortfolioSummary();
            summary.setOwnerPan(ownerPan);
            summary.setAmcId(entry.getKey());
            summary.setCostValue(entry.getValue().cost);
            summary.setMarketValue(entry.getValue().market);
            summary.setMatchCondition("SUMMARY_STATEMENT");
            summary.setRefreshFrom("SUMMARY_STATEMENT");
            summaries.save(summary);
        }
        MfPortfolioSummary total = new MfPortfolioSummary();
        total.setOwnerPan(ownerPan);
        total.setCostValue(sums.cost);
        total.setMarketValue(sums.market);
        total.setMatchCondition("SUMMARY_STATEMENT");
        total.setRefreshFrom("SUMMARY_STATEMENT");
        total.setTotal(true);
        summaries.save(total);

        reviewRows.saveAll(rows);
        batch.setStatus("ACCEPTED");
        batch.setValidStatus("GREEN");
        batch.setAcceptedAt(LocalDateTime.now());
        batches.save(batch);
        return Map.of("batch", batch, "folios", folioIndex.size(), "schemes", rows.size(), "status", "GREEN");
    }

    private static MfFolio findRelatedFolio(String candidate, java.util.Collection<MfFolio> existing) {
        MfFolio match = null;
        for (MfFolio folio : existing) {
            if (!MfFolioMatcher.related(candidate, folio.getFolioNo())) continue;
            if (match != null && !Objects.equals(match.getId(), folio.getId())) return null;
            match = folio;
        }
        return match;
    }

    private MfFolioScheme findSchemeByFolioAndIsin(Long folioId, String isin) {
        if (isin == null || isin.isBlank()) return null;
        return schemes.findByFolioId(folioId).stream()
                .filter(s -> isin.equalsIgnoreCase(Objects.toString(s.getIsin(), ""))).findFirst().orElse(null);
    }

    private MfImportBatch loadSummaryBatch(Long id, String ownerPan) {
        MfImportBatch batch = batches.findByIdAndPan(id, ownerPan).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Summary import was not found for this client."));
        if (!"SUMMARY".equals(batch.getStatementKind())) throw new IllegalArgumentException("This import is not a summary statement.");
        return batch;
    }

    private MfSummaryReport readBatch(MfImportBatch batch) {
        try { return mapper.readValue(batch.getParsedJson(), MfSummaryReport.class); }
        catch (Exception e) { throw new IllegalStateException("Stored summary statement cannot be read.", e); }
    }

    private MfAmc findOrCreateAmc(String displayName) {
        MfAmc amc = amcResolver.resolve(displayName);
        if (amc == null) throw new IllegalArgumentException("AMC name is required.");
        return amc;
    }

    private static Totals total(List<MfSummaryReconciliationLine> lines) {
        Totals total = new Totals();
        for (MfSummaryReconciliationLine line : lines) {
            total.cost = total.cost.add(nvl(line.getCostValue()));
            total.market = total.market.add(nvl(line.getMarketValue()));
        }
        return total;
    }

    private static boolean within(BigDecimal expected, BigDecimal actual) {
        BigDecimal e = nvl(expected);
        BigDecimal a = nvl(actual);
        BigDecimal base = e.abs().max(a.abs());
        if (base.signum() == 0) return true;
        return a.subtract(e).abs().compareTo(base.multiply(TOLERANCE_PERCENT)
                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)) <= 0;
    }

    private static String emailKey(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String normalize(String value) {
        return MfAmcResolver.normalize(value);
    }
    private static String key(String value) { return value == null ? "" : MfFolioMatcher.canonical(value); }
    private static String schemeKey(String folio, String isin, String scheme) {
        return key(folio) + "|" + (isin == null ? "" : isin.toUpperCase(Locale.ROOT)) + "|" + normalize(scheme);
    }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BigDecimal nvl(BigDecimal value) { return value == null ? ZERO : value; }
    private static final class Totals {
        BigDecimal cost = ZERO;
        BigDecimal market = ZERO;
    }
}
