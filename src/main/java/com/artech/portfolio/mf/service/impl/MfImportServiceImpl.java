package com.artech.portfolio.mf.service.impl;

import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.entity.MfFolio;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.entity.MfFolioSchemeNominee;
import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import com.artech.portfolio.mf.entity.tmp.MfTmpFolio;
import com.artech.portfolio.mf.entity.tmp.MfTmpNominee;
import com.artech.portfolio.mf.entity.tmp.MfTmpPortfolioSummary;
import com.artech.portfolio.mf.entity.tmp.MfTmpScheme;
import com.artech.portfolio.mf.entity.tmp.MfTmpTransaction;
import com.artech.portfolio.mf.entity.tmp.MfTmpValuation;
import com.artech.portfolio.mf.parser.KfCasParser;
import com.artech.portfolio.mf.parser.KfCasReport;
import com.artech.portfolio.mf.parser.TxnClassifier;
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
import com.artech.portfolio.mf.service.MfAmcResolver;
import com.artech.portfolio.mf.util.MfTxnIdentity;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * KFINTECH CAS upload (MF.md 1-3, 9, 10, 16, 17).
 *
 * <p>The statement is read completely before a single row is written: the
 * temporary tables are only emptied once the pdf has been parsed successfully,
 * so a report that cannot be read leaves the last good upload in place
 * (MF.md 9). A first import additionally gets the ADJUSTED ledger rows that
 * close the gap between the 5 year ledger and the statement's own current
 * totals (MF.md 10).</p>
 */
@Service
public class MfImportServiceImpl implements MfImportService {

    /** Description of the rows the first import adds (MF.md 10). */
    static final String ADJUSTED_DESCRIPTION = "ADJUSTED UNIT, AMOUNT TRANSACTION";

    /** Statement dates are printed as "25-Oct-2026". */
    private static final java.time.format.DateTimeFormatter PRINTED_DATE =
            java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MfImportBatchRepository batchRepository;

    private final MfTmpPortfolioSummaryRepository tmpSummaryRepository;
    private final MfTmpFolioRepository tmpFolioRepository;
    private final MfTmpSchemeRepository tmpSchemeRepository;
    private final MfTmpNomineeRepository tmpNomineeRepository;
    private final MfTmpValuationRepository tmpValuationRepository;
    private final MfTmpTransactionRepository tmpTransactionRepository;

    private final MfPortfolioSummaryRepository summaryRepository;
    private final MfFolioRepository folioRepository;
    private final MfFolioSchemeRepository schemeRepository;
    private final MfFolioSchemeNomineeRepository nomineeRepository;
    private final MfFolioSchemeValuationRepository valuationRepository;
    private final MfFolioSchemeTransactionRepository transactionRepository;

    private final TransactionTemplate transactions;
    private final MfAmcResolver amcResolver;
    private final ObjectMapper mapper = new ObjectMapper();

    public MfImportServiceImpl(MfImportBatchRepository batchRepository,
                               MfTmpPortfolioSummaryRepository tmpSummaryRepository,
                               MfTmpFolioRepository tmpFolioRepository,
                               MfTmpSchemeRepository tmpSchemeRepository,
                               MfTmpNomineeRepository tmpNomineeRepository,
                               MfTmpValuationRepository tmpValuationRepository,
                               MfTmpTransactionRepository tmpTransactionRepository,
                               MfPortfolioSummaryRepository summaryRepository,
                               MfFolioRepository folioRepository,
                               MfFolioSchemeRepository schemeRepository,
                               MfFolioSchemeNomineeRepository nomineeRepository,
                               MfFolioSchemeValuationRepository valuationRepository,
                               MfFolioSchemeTransactionRepository transactionRepository,
                               PlatformTransactionManager transactionManager,
                               MfAmcResolver amcResolver) {
        this.batchRepository = batchRepository;
        this.tmpSummaryRepository = tmpSummaryRepository;
        this.tmpFolioRepository = tmpFolioRepository;
        this.tmpSchemeRepository = tmpSchemeRepository;
        this.tmpNomineeRepository = tmpNomineeRepository;
        this.tmpValuationRepository = tmpValuationRepository;
        this.tmpTransactionRepository = tmpTransactionRepository;
        this.summaryRepository = summaryRepository;
        this.folioRepository = folioRepository;
        this.schemeRepository = schemeRepository;
        this.nomineeRepository = nomineeRepository;
        this.valuationRepository = valuationRepository;
        this.transactionRepository = transactionRepository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.amcResolver = amcResolver;
    }

    // ----------------------------------------------------------------- upload

    @Override
    public Map<String, Object> importFile(String ownerPan, String fileName, byte[] data, String password) {
        // Read first, write later: a protected or unreadable file never touches
        // the temporary tables (MF.md 3/9).
        KfCasReport report = parse(data, password);
        // MF_V3 4 - a statement only belongs to the client whose PAN it carries.
        checkOwnerPan(ownerPan, report);
        String json = toJson(report);

        Staging staging = transactions.execute(status -> stage(ownerPan, report));

        MfImportBatch batch = new MfImportBatch();
        batch.setPan(ownerPan);
        batch.setFileName(fileName);
        batch.setFileType(report.fileType);
        batch.setReportFrom(printedDate(report.statementPeriod.from));
        batch.setReportTo(printedDate(report.statementPeriod.to));
        batch.setClientName(report.investorInfo.name);
        batch.setParsedJson(json);
        batch.setFolioCount(staging.folios());
        batch.setSchemeCount(staging.schemes());
        batch.setTxnCount(staging.txns());
        batch.setAdjustedCount(staging.adjusted());
        batchRepository.save(batch);

        return Map.of("batch", batch,
                "firstImport", staging.firstImport(),
                "warnings", report.parseWarnings);
    }

    /**
     * MF_V3 4 - the PAN printed on the statement's folios must be the PAN of
     * the signed in client. The comparison ignores the case the statement
     * prints it in; a statement without a PAN cannot be checked, so it is
     * accepted rather than blocked.
     */
    static void checkOwnerPan(String ownerPan, KfCasReport report) {
        Set<String> statementPans = new LinkedHashSet<>();
        for (KfCasReport.Folio folio : report.folios) {
            if (folio.pan != null && !folio.pan.isBlank()) {
                statementPans.add(folio.pan.trim());
            }
        }
        if (statementPans.isEmpty() || ownerPan == null || ownerPan.isBlank()) {
            return;
        }
        for (String pan : statementPans) {
            if (pan.equalsIgnoreCase(ownerPan.trim())) {
                return;
            }
        }
        throw new RuntimeException("This Mutual Fund statement belongs to PAN "
                + String.join(", ", statementPans)
                + ", so it cannot be uploaded by the account you are signed in with ("
                + ownerPan + ").");
    }

    @Override
    public List<MfImportBatch> listBatches(String ownerPan) {
        return batchRepository.findByPanOrderByUploadedAtDesc(ownerPan);
    }

    @Override
    public String parsedJson(Long batchId, String ownerPan) {
        return load(batchId, ownerPan).getParsedJson();
    }

    // ----------------------------------------------------------------- accept

    /**
     * MF.md 13 / MF_V3 3 - the reviewed statement is merged into the accepted
     * data instead of replacing it: a folio, a scheme or a ledger row that is
     * already on file keeps its row and only what the statement adds is
     * written, so an upload can never drop an older transaction. The printed
     * summary and the balances of a scheme follow the statement only while
     * that statement is the newest one accepted (MF_V3 6) - an older statement
     * can never move a total.
     */
    @Override
    public Map<String, Object> accept(Long batchId, String ownerPan) {
        MfImportBatch batch = load(batchId, ownerPan);
        return transactions.execute(status -> {
            List<MfTmpFolio> tmpFolios = tmpFolioRepository.findByOwnerPanOrderByFolioNo(ownerPan);
            if (tmpFolios.isEmpty()) {
                throw new RuntimeException("There is no parsed statement to accept - upload a report first");
            }
            boolean newest = isNewerThanAccepted(batch, ownerPan);
            Merge merged = merge(ownerPan, tmpFolios, newest);

            batch.setStatus("ACCEPTED");
            batch.setAcceptedAt(LocalDateTime.now());
            batch.setValidStatus("GREEN");
            batchRepository.save(batch);
            return Map.of("batch", batch,
                    "folios", merged.folios(),
                    "schemes", merged.schemes(),
                    "transactions", merged.transactions(),
                    "summaryUpdated", newest);
        });
    }

    @Override
    public void discard(Long batchId, String ownerPan) {
        MfImportBatch batch = load(batchId, ownerPan);
        if ("ACCEPTED".equals(batch.getStatus())) {
            throw new IllegalArgumentException("An accepted import cannot be discarded.");
        }
        transactions.executeWithoutResult(status -> purgeTemporary(ownerPan));
        batch.setStatus("DISCARDED");
        batch.setValidStatus("PENDING");
        batchRepository.save(batch);
    }

    // ---------------------------------------------------------------- staging

    /** Result of writing one report into the temporary tables. */
    private record Staging(int folios, int schemes, int txns, int adjusted, boolean firstImport) {
    }

    /**
     * Replaces the temporary set with the freshly parsed report. Called inside
     * the upload transaction, after the pdf has been read (MF.md 9).
     */
    private Staging stage(String ownerPan, KfCasReport report) {
        purgeTemporary(ownerPan);

        List<MfTmpPortfolioSummary> summaries = new ArrayList<>();
        for (KfCasReport.SummaryRow row : report.summary) {
            MfTmpPortfolioSummary summary = new MfTmpPortfolioSummary();
            summary.setOwnerPan(ownerPan);
            summary.setAmc(row.total ? "Total" : row.amc);
            if (!row.total) {
                var amc = amcResolver.resolve(row.amc);
                if (amc != null) summary.setAmcId(amc.getId());
            }
            summary.setMatchCondition("DETAIL_STATEMENT");
            summary.setCostValue(dec(row.cost));
            summary.setMarketValue(dec(row.market));
            summary.setTotal(row.total);
            summaries.add(summary);
        }
        tmpSummaryRepository.saveAll(summaries);

        List<MfTmpFolio> folios = new ArrayList<>();
        for (KfCasReport.Folio folio : report.folios) {
            MfTmpFolio entity = new MfTmpFolio();
            entity.setOwnerPan(ownerPan);
            entity.setFolioNo(folio.folio);
            var amc = amcResolver.resolve(folio.amc);
            if (amc != null) entity.setAmcId(amc.getId());
            entity.setRefreshFrom("DETAIL_STATEMENT");
            entity.setInvestorName(folio.name);
            entity.setFolioPan(folio.pan);
            entity.setKyc(folio.kyc);
            entity.setPanKyc(folio.panKyc);
            folios.add(entity);
        }
        tmpFolioRepository.saveAll(folios);

        List<KfCasReport.Scheme> parsedSchemes = new ArrayList<>();
        List<MfTmpScheme> schemes = new ArrayList<>();
        for (int i = 0; i < report.folios.size(); i++) {
            for (KfCasReport.Scheme scheme : report.folios.get(i).schemes) {
                MfTmpScheme entity = new MfTmpScheme();
                entity.setOwnerPan(ownerPan);
                entity.setFolioId(folios.get(i).getId());
                entity.setRefreshFrom("DETAIL_STATEMENT");
                entity.setScheme(scheme.scheme);
                entity.setAdvisor(scheme.advisor);
                entity.setRtaCode(scheme.rtaCode);
                entity.setRta(scheme.rta);
                entity.setFundType(scheme.type);
                entity.setIsin(scheme.isin);
                entity.setAmfi(scheme.amfi);
                entity.setOpenUnits(dec(scheme.open));
                entity.setCloseUnits(dec(scheme.close));
                entity.setCloseCalculated(dec(scheme.closeCalculated));
                schemes.add(entity);
                parsedSchemes.add(scheme);
            }
        }
        tmpSchemeRepository.saveAll(schemes);

        List<MfTmpNominee> nominees = new ArrayList<>();
        List<MfTmpValuation> valuations = new ArrayList<>();
        List<MfTmpTransaction> txns = new ArrayList<>();
        for (int i = 0; i < parsedSchemes.size(); i++) {
            KfCasReport.Scheme scheme = parsedSchemes.get(i);
            Long schemeId = schemes.get(i).getId();
            for (String name : scheme.nominees) {
                MfTmpNominee nominee = new MfTmpNominee();
                nominee.setOwnerPan(ownerPan);
                nominee.setSchemeId(schemeId);
                nominee.setNomineeName(name);
                nominees.add(nominee);
            }
            MfTmpValuation valuation = new MfTmpValuation();
            valuation.setOwnerPan(ownerPan);
            valuation.setSchemeId(schemeId);
            valuation.setValueDate(isoDate(scheme.valuation.date));
            valuation.setNav(dec(scheme.valuation.nav));
            valuation.setCostValue(dec(scheme.valuation.cost));
            valuation.setMarketValue(dec(scheme.valuation.value));
            valuations.add(valuation);
            for (KfCasReport.Txn txn : scheme.transactions) {
                MfTmpTransaction entity = new MfTmpTransaction();
                entity.setOwnerPan(ownerPan);
                entity.setSchemeId(schemeId);
                entity.setTxnDate(isoDate(txn.date));
                entity.setDescription(txn.description);
                entity.setAmount(dec(txn.amount));
                entity.setUnits(dec(txn.units));
                entity.setNav(dec(txn.nav));
                entity.setBalance(dec(txn.balance));
                entity.setType(txn.type);
                entity.setDividendRate(dec(txn.dividendRate));
                entity.setGiftFolio(txn.giftFolio);
                txns.add(entity);
            }
        }
        tmpNomineeRepository.saveAll(nominees);
        tmpValuationRepository.saveAll(valuations);
        tmpTransactionRepository.saveAll(txns);

        boolean firstImport = permanentIsEmpty(ownerPan);
        int adjusted = firstImport ? addAdjustedRows(ownerPan, schemes, valuations, txns) : 0;
        return new Staging(folios.size(), schemes.size(), txns.size() + adjusted, adjusted, firstImport);
    }

    /**
     * MF.md 10: the ledger only covers the last years the statement prints,
     * while the summary always holds the current worth of the investment. The
     * difference is booked once per scheme as an ADJUSTED row, dated on the
     * statement's own valuation date, so the ledger adds up to the summary at
     * scheme level - and, by summation, at AMC level too.
     */
    private int addAdjustedRows(String ownerPan, List<MfTmpScheme> schemes,
                                List<MfTmpValuation> valuations, List<MfTmpTransaction> txns) {
        Map<Long, MfTmpValuation> valuationByScheme = new HashMap<>();
        for (MfTmpValuation valuation : valuations) {
            valuationByScheme.put(valuation.getSchemeId(), valuation);
        }
        Map<Long, BigDecimal> amountByScheme = new TreeMap<>();
        Map<Long, BigDecimal> unitsByScheme = new TreeMap<>();
        for (MfTmpTransaction txn : txns) {
            if (TxnClassifier.TYPE_ADJUSTED.equals(txn.getType())) {
                continue;
            }
            amountByScheme.merge(txn.getSchemeId(), nvl(txn.getAmount()), BigDecimal::add);
            unitsByScheme.merge(txn.getSchemeId(), nvl(txn.getUnits()), BigDecimal::add);
        }
        List<MfTmpTransaction> adjusted = new ArrayList<>();
        for (MfTmpScheme scheme : schemes) {
            MfTmpValuation valuation = valuationByScheme.get(scheme.getId());
            if (valuation == null || valuation.getCostValue() == null) {
                continue; // nothing to reconcile the ledger against
            }
            BigDecimal amount = valuation.getCostValue()
                    .subtract(amountByScheme.getOrDefault(scheme.getId(), ZERO));
            BigDecimal units = nvl(scheme.getCloseUnits())
                    .subtract(nvl(scheme.getOpenUnits()).add(unitsByScheme.getOrDefault(scheme.getId(), ZERO)));
            if (amount.signum() == 0 && units.signum() == 0) {
                continue; // the ledger already reaches the summary
            }
            MfTmpTransaction txn = new MfTmpTransaction();
            txn.setOwnerPan(ownerPan);
            txn.setSchemeId(scheme.getId());
            txn.setTxnDate(valuation.getValueDate());
            txn.setDescription(ADJUSTED_DESCRIPTION);
            txn.setAmount(amount);
            txn.setUnits(units);
            txn.setNav(valuation.getNav());
            txn.setBalance(scheme.getCloseUnits());
            txn.setType(TxnClassifier.TYPE_ADJUSTED);
            adjusted.add(txn);
        }
        tmpTransactionRepository.saveAll(adjusted);
        return adjusted.size();
    }

    private boolean permanentIsEmpty(String ownerPan) {
        return folioRepository.findByOwnerPanOrderByFolioNo(ownerPan).isEmpty();
    }

    /** Empties the temporary set - only ever called after a successful read. */
    private void purgeTemporary(String ownerPan) {
        tmpTransactionRepository.deleteByOwner(ownerPan);
        tmpValuationRepository.deleteByOwner(ownerPan);
        tmpNomineeRepository.deleteByOwner(ownerPan);
        tmpSchemeRepository.deleteByOwner(ownerPan);
        tmpFolioRepository.deleteByOwner(ownerPan);
        tmpSummaryRepository.deleteByOwner(ownerPan);
    }

    /** What one accept added to the accepted data. */
    private record Merge(int folios, int schemes, int transactions) {
    }

    /**
     * MF_V3 3/6 - writes the staged statement into the permanent table set.
     * Folios and schemes are matched on their printed numbers, the ledger on
     * the identity of every row (MF.md 12a), so a row that is already on file
     * keeps its record and only what the statement adds is inserted. The
     * balances, the valuations and the summary are copied over only when
     * {@code newest} says the statement ends later than anything accepted so
     * far - an older statement must never move a total (MF_V3 6).
     */
    private Merge merge(String ownerPan, List<MfTmpFolio> tmpFolios, boolean newest) {
        // ----------------------------------------------------------- folios
        Map<String, MfFolio> folioByNo = new LinkedHashMap<>();
        Map<Long, String> folioNoById = new HashMap<>();
        Map<Long, MfFolio> folioById = new HashMap<>();
        for (MfFolio folio : folioRepository.findByOwnerPanOrderByFolioNo(ownerPan)) {
            folioByNo.put(key(folio.getFolioNo()), folio);
            folioNoById.put(folio.getId(), folio.getFolioNo());
            folioById.put(folio.getId(), folio);
        }
        Map<Long, Long> folioIdMap = new HashMap<>();       // staged id -> accepted id
        List<MfFolio> addedFolios = new ArrayList<>();
        List<MfTmpFolio> addedFolioSource = new ArrayList<>();
        for (MfTmpFolio row : tmpFolios) {
            MfFolio accepted = folioByNo.get(key(row.getFolioNo()));
            if (accepted == null) {
                MfFolio copy = new MfFolio();
                copy.setOwnerPan(row.getOwnerPan());
                copy.setFolioNo(row.getFolioNo());
                var amc = amcResolver.resolve(row.getAmc());
                if (amc != null) copy.setAmcId(amc.getId());
                copy.setRefreshFrom("DETAIL_STATEMENT");
                copy.setInvestorName(row.getInvestorName());
                copy.setFolioPan(row.getFolioPan());
                copy.setKyc(row.getKyc());
                copy.setPanKyc(row.getPanKyc());
                addedFolios.add(copy);
                addedFolioSource.add(row);
            } else {
                var amc = amcResolver.resolve(row.getAmc());
                if (amc != null) accepted.setAmcId(amc.getId());
                accepted.setRefreshFrom("DETAIL_STATEMENT");
                accepted.setInvestorName(row.getInvestorName());
                accepted.setFolioPan(row.getFolioPan());
                accepted.setKyc(row.getKyc());
                accepted.setPanKyc(row.getPanKyc());
                folioIdMap.put(row.getId(), accepted.getId());
            }
        }
        folioRepository.saveAll(addedFolios);
        for (MfFolio folio : addedFolios) folioByNo.put(key(folio.getFolioNo()), folio);
        for (int i = 0; i < addedFolios.size(); i++) {
            MfTmpFolio source = addedFolioSource.get(i);
            folioIdMap.put(source.getId(), addedFolios.get(i).getId());
            folioByNo.put(key(source.getFolioNo()), addedFolios.get(i));
            folioNoById.put(addedFolios.get(i).getId(), addedFolios.get(i).getFolioNo());
            folioById.put(addedFolios.get(i).getId(), addedFolios.get(i));
        }
        for (MfTmpFolio row : tmpFolios) {
            MfFolio accepted = folioByNo.get(key(row.getFolioNo()));
            if (accepted != null) folioIdMap.put(row.getId(), accepted.getId());
        }
        folioRepository.saveAll(folioByNo.values());

        // ---------------------------------------------------------- schemes
        Map<Long, Map<String, MfFolioScheme>> schemeIndex = new HashMap<>();
        for (MfFolioScheme scheme : schemeRepository.findByFolioIdIn(new HashSet<>(folioIdMap.values()))) {
            schemeIndex.computeIfAbsent(scheme.getFolioId(), x -> new LinkedHashMap<>())
                    .put(key(scheme.getScheme()), scheme);
        }
        List<MfTmpScheme> tmpSchemes = tmpSchemeRepository.findByOwnerPan(ownerPan);
        Map<Long, Long> schemeIdMap = new HashMap<>();       // staged id -> accepted id
        List<MfFolioScheme> addedSchemes = new ArrayList<>();
        List<MfTmpScheme> addedSchemeSource = new ArrayList<>();
        for (MfTmpScheme row : tmpSchemes) {
            Long folioId = folioIdMap.get(row.getFolioId());
            if (folioId == null) {
                continue;                                   // its folio was not staged
            }
            Map<String, MfFolioScheme> byName = schemeIndex.computeIfAbsent(folioId, x -> new LinkedHashMap<>());
            MfFolioScheme accepted = byName.get(key(row.getScheme()));
            if (accepted == null) {
                MfFolioScheme copy = new MfFolioScheme();
                copy.setOwnerPan(row.getOwnerPan());
                copy.setFolioId(folioId);
                copy.setRefreshFrom("DETAIL_STATEMENT");
                copy.setScheme(row.getScheme());
                copy.setAdvisor(row.getAdvisor());
                copy.setRtaCode(row.getRtaCode());
                copy.setRta(row.getRta());
                copy.setFundType(row.getFundType());
                copy.setIsin(row.getIsin());
                copy.setAmfi(row.getAmfi());
                copy.setOpenUnits(row.getOpenUnits());
                copy.setCloseUnits(row.getCloseUnits());
                copy.setCloseCalculated(row.getCloseCalculated());
                addedSchemes.add(copy);
                addedSchemeSource.add(row);
            } else {
                accepted.setAdvisor(row.getAdvisor());
                accepted.setRtaCode(row.getRtaCode());
                accepted.setRta(row.getRta());
                accepted.setFundType(row.getFundType());
                accepted.setIsin(row.getIsin());
                accepted.setAmfi(row.getAmfi());
                accepted.setRefreshFrom("DETAIL_STATEMENT");
                accepted.setOpenUnits(row.getOpenUnits());
                accepted.setCloseUnits(row.getCloseUnits());
                accepted.setCloseCalculated(row.getCloseCalculated());
                schemeIdMap.put(row.getId(), accepted.getId());
            }
        }
        schemeRepository.saveAll(addedSchemes);
        for (int i = 0; i < addedSchemes.size(); i++) {
            MfTmpScheme source = addedSchemeSource.get(i);
            schemeIdMap.put(source.getId(), addedSchemes.get(i).getId());
            schemeIndex.get(folioIdMap.get(source.getFolioId()))
                    .put(key(source.getScheme()), addedSchemes.get(i));
        }
        Set<Long> acceptedSchemeIds = new HashSet<>(schemeIdMap.values());

        // ------------------------------------------------------- valuations
        Map<Long, MfFolioSchemeValuation> valuationByScheme = new HashMap<>();
        for (MfFolioSchemeValuation value : valuationRepository.findBySchemeIdIn(acceptedSchemeIds)) {
            valuationByScheme.put(value.getSchemeId(), value);
        }
        List<MfFolioSchemeValuation> addedValuations = new ArrayList<>();
        for (MfTmpValuation row : tmpValuationRepository.findBySchemeIdIn(schemeIdMap.keySet())) {
            MfFolioSchemeValuation accepted = valuationByScheme.get(schemeIdMap.get(row.getSchemeId()));
            if (accepted == null) {
                MfFolioSchemeValuation copy = new MfFolioSchemeValuation();
                copy.setOwnerPan(row.getOwnerPan());
                copy.setSchemeId(schemeIdMap.get(row.getSchemeId()));
                copy.setValueDate(row.getValueDate());
                copy.setNav(row.getNav());
                copy.setCostValue(row.getCostValue());
                copy.setMarketValue(row.getMarketValue());
                addedValuations.add(copy);
            } else {
                accepted.setValueDate(row.getValueDate());
                accepted.setNav(row.getNav());
                accepted.setCostValue(row.getCostValue());
                accepted.setMarketValue(row.getMarketValue());
            }
        }
        valuationRepository.saveAll(addedValuations);
        for (MfFolioSchemeValuation copy : addedValuations) {
            valuationByScheme.put(copy.getSchemeId(), copy);
        }

        // --------------------------------------------------------- nominees
        Map<Long, List<MfFolioSchemeNominee>> nomineesByScheme = new HashMap<>();
        for (MfFolioSchemeNominee nominee : nomineeRepository.findBySchemeIdIn(acceptedSchemeIds)) {
            nomineesByScheme.computeIfAbsent(nominee.getSchemeId(), x -> new ArrayList<>()).add(nominee);
        }
        Map<Long, List<String>> namesByStagedScheme = new HashMap<>();
        for (MfTmpNominee row : tmpNomineeRepository.findBySchemeIdIn(schemeIdMap.keySet())) {
            namesByStagedScheme.computeIfAbsent(row.getSchemeId(), x -> new ArrayList<>())
                    .add(row.getNomineeName());
        }
        List<MfFolioSchemeNominee> staleNominees = new ArrayList<>();
        List<MfFolioSchemeNominee> addedNominees = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : namesByStagedScheme.entrySet()) {
            Long schemeId = schemeIdMap.get(entry.getKey());
            if (schemeId == null) {
                continue;
            }
            staleNominees.addAll(nomineesByScheme.getOrDefault(schemeId, List.of()));
            for (String name : entry.getValue()) {
                MfFolioSchemeNominee copy = new MfFolioSchemeNominee();
                copy.setOwnerPan(ownerPan);
                copy.setSchemeId(schemeId);
                copy.setNomineeName(name);
                addedNominees.add(copy);
            }
        }
        if (!staleNominees.isEmpty()) {
            nomineeRepository.deleteAll(staleNominees);
        }
        nomineeRepository.saveAll(addedNominees);

        // ----------------------------------------------------------- summary
        {                                                       // MF_V5 7: snapshot rows always follow the accepted report
            summaryRepository.deleteByOwner(ownerPan);
            List<MfPortfolioSummary> summaries = new ArrayList<>();
            for (MfTmpPortfolioSummary row : tmpSummaryRepository.findByOwnerPanOrderByAmc(ownerPan)) {
                MfPortfolioSummary copy = new MfPortfolioSummary();
                copy.setOwnerPan(row.getOwnerPan());
                copy.setAmcId(row.getAmcId());
                copy.setRefreshFrom("DETAIL_STATEMENT");
                copy.setMatchCondition("DETAIL_STATEMENT");
                copy.setCostValue(row.getCostValue());
                copy.setMarketValue(row.getMarketValue());
                copy.setTotal(row.isTotal());
                summaries.add(copy);
            }
            summaryRepository.saveAll(summaries);
        }

        // ------------------------------------------------------ transactions
        Map<Long, String> keyByScheme = new HashMap<>();
        for (Map.Entry<Long, Map<String, MfFolioScheme>> entry : schemeIndex.entrySet()) {
            for (MfFolioScheme scheme : entry.getValue().values()) {
                keyByScheme.put(scheme.getId(),
                        MfTxnIdentity.schemeKey(folioNoById.get(scheme.getFolioId()), scheme.getScheme()));
            }
        }
        Set<String> identities = new HashSet<>();
        for (MfFolioSchemeTransaction row : transactionRepository.findBySchemeIdIn(acceptedSchemeIds)) {
            identities.add(MfTxnIdentity.of(keyByScheme.get(row.getSchemeId()), row.getTxnDate(),
                    row.getType(), row.getDescription(), row.getAmount(), row.getUnits()));
        }
        List<MfFolioSchemeTransaction> addedTxns = new ArrayList<>();
        for (MfTmpTransaction row : tmpTransactionRepository.findBySchemeIdIn(schemeIdMap.keySet())) {
            Long schemeId = schemeIdMap.get(row.getSchemeId());
            String schemeKey = schemeId == null ? null : keyByScheme.get(schemeId);
            if (schemeKey == null) {
                continue;
            }
            // MF_V3 2 - a row already on file is checked, not written twice;
            // two identical rows the statement itself prints stay two rows
            if (identities.contains(MfTxnIdentity.of(schemeKey, row.getTxnDate(), row.getType(),
                    row.getDescription(), row.getAmount(), row.getUnits()))) {
                continue;
            }
            MfFolioSchemeTransaction copy = new MfFolioSchemeTransaction();
            copy.setOwnerPan(ownerPan);
            copy.setSchemeId(schemeId);
            copy.setTxnDate(row.getTxnDate());
            copy.setDescription(row.getDescription());
            copy.setAmount(row.getAmount());
            copy.setUnits(row.getUnits());
            copy.setNav(row.getNav());
            copy.setBalance(row.getBalance());
            copy.setType(row.getType());
            copy.setDividendRate(row.getDividendRate());
            copy.setGiftFolio(row.getGiftFolio());
            addedTxns.add(copy);
        }
        transactionRepository.saveAll(addedTxns);

        // Rebook only when the statement advances accepted history; an older
        // statement may refresh snapshot tables but must not rewrite ledger adjustments.
        if (newest) {
            Map<Long, MfFolioScheme> carried = new LinkedHashMap<>();
            Map<Long, MfFolioScheme> allSchemes = new LinkedHashMap<>();
            for (Map<String, MfFolioScheme> byName : schemeIndex.values()) {
                for (MfFolioScheme scheme : byName.values()) {
                    allSchemes.put(scheme.getId(), scheme);
                }
            }
            for (Long schemeId : schemeIdMap.values()) {
                MfFolioScheme scheme = allSchemes.get(schemeId);
                if (scheme != null) {
                    carried.put(schemeId, scheme);
                }
            }
            rebookAdjustments(ownerPan, carried, valuationByScheme);
        }

        return new Merge(addedFolios.size(), addedSchemes.size(), addedTxns.size());
    }

    /**
     * MF.md 10 / MF_V3 6 - one ADJUSTED row per scheme so that the ledger of
     * everything accepted - the rows already on file plus the rows this
     * statement adds - reaches the cost value and the closing balance the
     * newest statement prints. Rows booked by an earlier statement are dropped
     * first, otherwise an extension would carry two adjustments for the same
     * scheme.
     */
    private void rebookAdjustments(String ownerPan, Map<Long, MfFolioScheme> schemes,
                                   Map<Long, MfFolioSchemeValuation> valuations) {
        if (schemes.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> spentAmount = new TreeMap<>();
        Map<Long, BigDecimal> spentUnits = new TreeMap<>();
        List<MfFolioSchemeTransaction> stale = new ArrayList<>();
        for (MfFolioSchemeTransaction row : transactionRepository.findBySchemeIdIn(schemes.keySet())) {
            if (TxnClassifier.TYPE_ADJUSTED.equals(row.getType())) {
                stale.add(row);
            } else {
                spentAmount.merge(row.getSchemeId(), nvl(row.getAmount()), BigDecimal::add);
                spentUnits.merge(row.getSchemeId(), nvl(row.getUnits()), BigDecimal::add);
            }
        }
        List<MfFolioSchemeTransaction> fresh = new ArrayList<>();
        for (MfFolioScheme scheme : schemes.values()) {
            MfFolioSchemeValuation valuation = valuations.get(scheme.getId());
            if (valuation == null || valuation.getCostValue() == null) {
                continue;                       // nothing to reconcile against
            }
            BigDecimal amount = valuation.getCostValue()
                    .subtract(spentAmount.getOrDefault(scheme.getId(), ZERO));
            BigDecimal units = nvl(scheme.getCloseUnits())
                    .subtract(nvl(scheme.getOpenUnits()).add(spentUnits.getOrDefault(scheme.getId(), ZERO)));
            if (amount.signum() == 0 && units.signum() == 0) {
                continue;                       // the ledger already reaches the summary
            }
            MfFolioSchemeTransaction row = new MfFolioSchemeTransaction();
            row.setOwnerPan(ownerPan);
            row.setSchemeId(scheme.getId());
            row.setTxnDate(valuation.getValueDate());
            row.setDescription(ADJUSTED_DESCRIPTION);
            row.setAmount(amount);
            row.setUnits(units);
            row.setNav(valuation.getNav());
            row.setBalance(scheme.getCloseUnits());
            row.setType(TxnClassifier.TYPE_ADJUSTED);
            fresh.add(row);
        }
        if (!stale.isEmpty()) {
            transactionRepository.deleteAll(stale);
        }
        transactionRepository.saveAll(fresh);
    }

    /**
     * MF_V3 6 - the printed summary always holds the current worth of the
     * investment, so it may only be copied over by a statement whose period
     * ends no earlier than everything accepted before it. A statement that
     * ends earlier is history: it can still add a missing transaction, but it
     * can never move a total.
     */
    private boolean isNewerThanAccepted(MfImportBatch batch, String ownerPan) {
        LocalDate acceptedTo = batchRepository.findByPanAndStatusOrderByUploadedAtDesc(ownerPan, "ACCEPTED")
                .stream()
                .filter(other -> !Objects.equals(other.getId(), batch.getId()))
                .map(MfImportBatch::getReportTo)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        return acceptedTo == null || batch.getReportTo() == null || !batch.getReportTo().isBefore(acceptedTo);
    }

    /** Matching key of a printed number or name across the two table sets. */
    private static String key(String printed) {
        return printed == null ? "" : printed.trim();
    }

    // --------------------------------------------------------------- helpers

    private MfImportBatch load(Long batchId, String ownerPan) {
        return batchRepository.findByIdAndPan(batchId, ownerPan).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Import " + batchId + " was not found for this client"));
    }

    private String toJson(KfCasReport report) {
        // Jackson 3 (tools.jackson) - the same mapper the parsed JSON sample is
        // written with, so the download matches the report's own JSON shape.
        return mapper.writeValueAsString(report);
    }

    /**
     * The parser decides the message the screen shows: a protected file without
     * a password, a wrong password, or a file that is not a statement
     * (MF.md 3). Anything else - a broken pdf - falls back to the generic one.
     */
    private static KfCasReport parse(byte[] data, String password) {
        try {
            return new KfCasParser().parse(data, password);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(KfCasParser.UNREADABLE_MSG, e);
        }
    }

    private static BigDecimal dec(String printed) {
        return printed == null || printed.isBlank() ? null : new BigDecimal(printed);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** "2022-11-25" -> LocalDate, tolerating the printed "25-Nov-2022" form. */
    private static LocalDate isoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return printedDate(value);
        }
    }

    private static LocalDate printedDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim(), PRINTED_DATE);
    }
}
