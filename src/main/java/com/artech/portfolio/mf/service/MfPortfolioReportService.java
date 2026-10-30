package com.artech.portfolio.mf.service;

import com.artech.portfolio.mf.entity.MfAmc;
import com.artech.portfolio.mf.entity.MfFolio;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import com.artech.portfolio.mf.parser.TxnClassifier;
import com.artech.portfolio.mf.repository.MfAmcRepository;
import com.artech.portfolio.mf.repository.MfFolioRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeTransactionRepository;
import com.artech.portfolio.mf.repository.MfFolioSchemeValuationRepository;
import com.artech.portfolio.mf.repository.MfPortfolioSummaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read model of the three mutual fund portfolio reports (MFR_V9):
 *
 * <ol>
 *   <li>the AMC level summary of the accepted statement,</li>
 *   <li>one AMC's schemes and ledger,</li>
 *   <li>the ledger across every AMC and scheme.</li>
 * </ol>
 *
 * <p>All three accept a date range. Without one the money columns are the
 * accepted statement snapshot (summary rows and valuations). With one they are
 * derived from the accepted ledger (MFR_V9 5): <b>cost</b> is the sum of the
 * signed amounts inside the range and <b>market value</b> is the units moved
 * inside the range times the last NAV printed in it for that scheme. A range
 * therefore answers "what did I put in and what is it worth for the trades of
 * these dates".</p>
 *
 * <p>The ledger is read once per request for the whole owner and answered from
 * memory - every report needs the scheme/AMC level grouping anyway, and a
 * person's statement is a few thousand rows.</p>
 *
 * <p>MFR_V10 2/3: a row classified {@code STAMP_DUTY_TAX} is folded into the
 * purchase of the same date and scheme before anything is filtered, counted or
 * paged, so no report ever shows a stamp duty line of its own.</p>
 */
@Service
public class MfPortfolioReportService {

    /** Default rows per page (MFR_V9 2/6, MFR_V10 4). */
    public static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    /** Sentinels: the repository range scan is never given a null date. */
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2999, 12, 31);

    private final MfFolioRepository folios;
    private final MfFolioSchemeRepository schemes;
    private final MfFolioSchemeTransactionRepository transactions;
    private final MfFolioSchemeValuationRepository valuations;
    private final MfPortfolioSummaryRepository summaries;
    private final MfAmcRepository amcs;

    public MfPortfolioReportService(MfFolioRepository folios,
            MfFolioSchemeRepository schemes,
            MfFolioSchemeTransactionRepository transactions,
            MfFolioSchemeValuationRepository valuations,
            MfPortfolioSummaryRepository summaries,
            MfAmcRepository amcs) {
        this.folios = folios;
        this.schemes = schemes;
        this.transactions = transactions;
        this.valuations = valuations;
        this.summaries = summaries;
        this.amcs = amcs;
    }

    // ------------------------------------------------------------- summary --
    /** AMC level report of the portfolio page (MFR_V9 3). */
    public Map<String, Object> summary(String ownerPan, LocalDate from, LocalDate to, Long amcId) {
        Snapshot snap = snapshot(ownerPan, from, to);
        boolean derived = isRanged(from, to);
        List<MfPortfolioSummary> accepted = summaries.findByOwnerPanOrderByAmc(ownerPan).stream()
                .filter(row -> !row.isTotal())
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMarket = BigDecimal.ZERO;

        if (derived) {
            Map<Long, Sheet> byAmc = derivedByAmc(snap);
            List<Long> ids = byAmc.keySet().stream()
                    .filter(id -> amcId == null || amcId.equals(id))
                    .sorted(Comparator.comparing(id -> Objects.toString(snap.amcName(id), ""),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (Long id : ids) {
                Sheet sheet = byAmc.get(id);
                rows.add(row(id, snap.amcName(id), sheet, sheet.count()));
                totalCost = totalCost.add(sheet.cost());
                totalMarket = totalMarket.add(sheet.market());
            }
        } else {
            for (MfPortfolioSummary row : accepted) {
                if (amcId != null && !amcId.equals(row.getAmcId())) {
                    continue;
                }
                BigDecimal cost = nullSafeAmount(row.getCostValue());
                BigDecimal market = nullSafeAmount(row.getMarketValue());
                rows.add(row(row.getAmcId(), row.getAmc(), new Sheet(cost, market, null, 0), null));
                totalCost = totalCost.add(cost);
                totalMarket = totalMarket.add(market);
            }
        }

        if (!derived) {
            // the statement prints its own Total line - prefer it over our sum
            MfPortfolioSummary printedTotal = summaries.findTotalByOwner(ownerPan).orElse(null);
            if (printedTotal != null && amcId == null) {
                totalCost = nullSafeAmount(printedTotal.getCostValue());
                totalMarket = nullSafeAmount(printedTotal.getMarketValue());
            }
        }

        return Map.of(
                "from", text(from), "to", text(to), "derived", derived,
                "rows", rows,
                "total", Map.of("costValue", totalCost, "marketValue", totalMarket),
                "amcs", amcOptions(snap, accepted));
    }

    // -------------------------------------------------------------- detail --
    /** One AMC: its schemes and its ledger (MFR_V9 4/5/7). */
    public Map<String, Object> detail(String ownerPan, Long amcId, LocalDate from, LocalDate to,
            String schemeName, int page, int size) {
        Snapshot snap = snapshot(ownerPan, from, to);
        boolean derived = isRanged(from, to);
        List<MfFolioScheme> amcSchemes = snap.schemesOfAmc(amcId);
        List<MfPortfolioSummary> accepted = summaries.findHoldingsByOwnerAndAmc(ownerPan, amcId);
        if (amcSchemes.isEmpty() && accepted.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Mutual fund AMC portfolio was not found.");
        }
        String amcName = accepted.stream().map(MfPortfolioSummary::getAmc)
                .filter(Objects::nonNull).findFirst().orElse(snap.amcName(amcId));

        Map<Long, Movement> movements = movementsByScheme(snap);
        String filter = normalize(schemeName);
        List<MfFolioScheme> shown = amcSchemes.stream()
                .filter(scheme -> filter == null || normalize(scheme.getScheme()).contains(filter))
                .sorted(Comparator.comparing(scheme -> Objects.toString(scheme.getScheme(), ""),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        Set<Long> shownIds = shown.stream().map(MfFolioScheme::getId)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, MfFolioSchemeValuation> valuationByScheme = latestValuations(shownIds);
        List<Map<String, Object>> schemeRows = new ArrayList<>();
        Sheet total = Sheet.EMPTY;
        for (MfFolioScheme scheme : shown) {
            MfFolio folio = snap.folioOfScheme(scheme.getId());
            MfFolioSchemeValuation valuation = valuationByScheme.get(scheme.getId());
            Movement movement = movements.getOrDefault(scheme.getId(), new Movement());
            BigDecimal cost = derived ? movement.amount
                    : valuation == null ? null : valuation.getCostValue();
            BigDecimal market = derived ? marketValue(movement, valuation)
                    : valuation == null ? null : valuation.getMarketValue();
            BigDecimal nav = derived ? navOf(movement, valuation)
                    : valuation == null ? null : valuation.getNav();
            LocalDate navDate = derived ? navDateOf(movement, valuation)
                    : valuation == null ? null : valuation.getValueDate();
            schemeRows.add(Map.ofEntries(
                    Map.entry("id", scheme.getId()),
                    Map.entry("folioNo", nullSafe(folio == null ? null : folio.getFolioNo())),
                    Map.entry("scheme", nullSafe(scheme.getScheme())),
                    Map.entry("isin", nullSafe(scheme.getIsin())),
                    Map.entry("rta", nullSafe(scheme.getRta())),
                    Map.entry("rtaCode", nullSafe(scheme.getRtaCode())),
                    Map.entry("openUnits", nullSafe(scheme.getOpenUnits())),
                    Map.entry("closeUnits", nullSafe(scheme.getCloseUnits())),
                    Map.entry("units", movement.units),
                    Map.entry("txnCount", movement.count),
                    Map.entry("nav", nullSafe(nav)),
                    Map.entry("navDate", nullSafe(navDate == null ? null : navDate.toString())),
                    Map.entry("costValue", nullSafe(cost)),
                    Map.entry("marketValue", nullSafe(market))));
            total = total.plus(new Sheet(nullSafeAmount(cost), nullSafeAmount(market),
                    movement.units, movement.count));
        }

        List<Entry> ledger = snap.rows.stream()
                .filter(entry -> shownIds.contains(entry.schemeId()))
                .toList();
        int safeSize = pageSize(size);
        int safePage = pageNumber(page);
        List<Map<String, Object>> txnRows = new ArrayList<>();
        for (Entry entry : pageOf(ledger, safePage, safeSize)) {
            txnRows.add(txnRow(snap, entry));
        }

        return Map.ofEntries(
                Map.entry("amcId", amcId),
                Map.entry("amc", nullSafe(amcName)),
                Map.entry("from", text(from)), Map.entry("to", text(to)),
                Map.entry("scheme", nullSafe(schemeName)),
                Map.entry("derived", derived),
                Map.entry("costValue", total.cost()),
                Map.entry("marketValue", total.market()),
                Map.entry("units", total.units() == null ? BigDecimal.ZERO : total.units()),
                Map.entry("txnCount", ledger.size()),
                Map.entry("schemeNames", snap.schemeNamesOfAmc(amcId)),
                Map.entry("schemes", schemeRows),
                Map.entry("transactions", txnRows),
                Map.entry("pagination", pagination(safePage, safeSize, ledger.size())));
    }

    // -------------------------------------------------------- transactions --
    /** Every accepted ledger row of the owner (MFR_V9 1/2). */
    public Map<String, Object> transactions(String ownerPan, LocalDate from, LocalDate to,
            Long amcId, String schemeName, String search, int page, int size) {
        Snapshot snap = snapshot(ownerPan, from, to);
        String schemeFilter = normalize(schemeName);
        String searchFilter = normalize(search);
        List<Entry> filtered = snap.rows.stream()
                .filter(entry -> amcId == null || amcId.equals(snap.amcOfScheme(entry.schemeId())))
                .filter(entry -> schemeFilter == null
                        || normalize(snap.schemeName(entry.schemeId())).contains(schemeFilter))
                .filter(entry -> searchFilter == null
                        || normalize(snap.schemeName(entry.schemeId())).contains(searchFilter)
                        || normalize(entry.txn().getDescription()).contains(searchFilter))
                .toList();

        Map<Long, Movement> perScheme = new HashMap<>();
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal units = BigDecimal.ZERO;
        for (Entry entry : filtered) {
            perScheme.computeIfAbsent(entry.schemeId(), id -> new Movement()).add(entry);
            amount = amount.add(entry.amount());
            units = units.add(nullSafeAmount(entry.txn().getUnits()));
        }
        BigDecimal market = BigDecimal.ZERO;
        for (Movement movement : perScheme.values()) {
            market = market.add(marketValue(movement, null));
        }

        int safeSize = pageSize(size);
        int safePage = pageNumber(page);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Entry entry : pageOf(filtered, safePage, safeSize)) {
            rows.add(txnRow(snap, entry));
        }

        return Map.ofEntries(
                Map.entry("from", text(from)), Map.entry("to", text(to)),
                Map.entry("derived", isRanged(from, to)),
                Map.entry("rows", rows),
                Map.entry("pagination", pagination(safePage, safeSize, filtered.size())),
                Map.entry("totals", Map.of("count", filtered.size(), "amount", amount,
                        "units", units, "marketValue", market)),
                Map.entry("amcs", amcOptions(snap, List.of())),
                Map.entry("schemeNames", snap.allSchemeNames()));
    }

    // ------------------------------------------------------------ snapshot --
    private Snapshot snapshot(String ownerPan, LocalDate from, LocalDate to) {
        Snapshot snap = new Snapshot();
        for (MfFolio folio : folios.findByOwnerPanOrderByFolioNo(ownerPan)) {
            snap.folios.put(folio.getId(), folio);
        }
        for (MfFolioScheme scheme : schemes.findByOwnerPan(ownerPan)) {
            snap.schemes.put(scheme.getId(), scheme);
        }
        for (MfAmc amc : amcs.findAll()) {
            snap.amcs.put(amc.getId(), amc);
        }
        snap.rows.addAll(foldStampDuty(transactions.findForOwnerBetween(ownerPan,
                from == null ? EARLIEST : from, to == null ? LATEST : to)));
        return snap;
    }

    /**
     * MFR_V10 2/3: the CAS prints the stamp duty of a purchase as its own row on
     * the same date and scheme, so the two are one movement. The stamp duty row
     * is dropped and its amount is added to the row it belongs to
     * (4499.78 + 0.22 = 4500.00). Filtering, counting and paging all run after
     * this, so a stamp duty line can never show up - not on its own and not as a
     * row of a page.
     */
    private static List<Entry> foldStampDuty(List<MfFolioSchemeTransaction> txns) {
        List<Entry> entries = new ArrayList<>(txns.size());
        Map<Key, List<Integer>> byDateAndScheme = new HashMap<>();
        for (MfFolioSchemeTransaction txn : txns) {
            entries.add(new Entry(txn, nullSafeAmount(txn.getAmount())));
            byDateAndScheme.computeIfAbsent(new Key(txn.getSchemeId(), txn.getTxnDate()),
                    key -> new ArrayList<>()).add(entries.size() - 1);
        }
        Set<Integer> stampDuty = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            if (!isStampDuty(entries.get(index))) {
                continue;
            }
            stampDuty.add(index);
            int parent = stampDutyParent(entries, byDateAndScheme.get(keyOf(entries.get(index))), index);
            if (parent >= 0) {
                Entry target = entries.get(parent);
                entries.set(parent, new Entry(target.txn(),
                        target.amount().add(entries.get(index).amount())));
            }
        }
        List<Entry> folded = new ArrayList<>(entries.size() - stampDuty.size());
        for (int index = 0; index < entries.size(); index++) {
            if (!stampDuty.contains(index)) {
                folded.add(entries.get(index));
            }
        }
        return folded;
    }

    /**
     * The row a stamp duty line belongs to: same scheme and same date, the SIP
     * instalment first, then any other purchase/switch in, then the closest row
     * of that day. {@code -1} when the day holds no other row at all.
     */
    private static int stampDutyParent(List<Entry> entries, List<Integer> sameDay, int stampDutyIndex) {
        int parent = -1;
        int parentRank = Integer.MAX_VALUE;
        int parentDistance = Integer.MAX_VALUE;
        for (int candidate : sameDay) {
            if (candidate == stampDutyIndex || isStampDuty(entries.get(candidate))) {
                continue;
            }
            int rank = parentRank(entries.get(candidate));
            int distance = Math.abs(candidate - stampDutyIndex);
            if (rank < parentRank || (rank == parentRank && distance < parentDistance)) {
                parent = candidate;
                parentRank = rank;
                parentDistance = distance;
            }
        }
        return parent;
    }

    /** Preference of the rows a stamp duty line may be folded into, lowest first. */
    private static int parentRank(Entry entry) {
        String type = entry.txn().getType();
        if (TxnClassifier.TYPE_PURCHASE_SIP.equals(type)) {
            return 0;
        }
        if (TxnClassifier.TYPE_PURCHASE.equals(type)
                || TxnClassifier.TYPE_SWITCH_IN.equals(type)
                || TxnClassifier.TYPE_SWITCH_IN_MERGER.equals(type)
                || TxnClassifier.TYPE_DIVIDEND_REINVEST.equals(type)) {
            return 1;
        }
        return entry.amount().signum() > 0 && entry.txn().getUnits() != null ? 2 : 3;
    }

    private static boolean isStampDuty(Entry entry) {
        return TxnClassifier.TYPE_STAMP_DUTY_TAX.equals(entry.txn().getType());
    }

    private static Key keyOf(Entry entry) {
        return new Key(entry.schemeId(), entry.txn().getTxnDate());
    }

    /** Scheme id -> everything the ledger did for it inside the range. */
    private static Map<Long, Movement> movementsByScheme(Snapshot snap) {
        Map<Long, Movement> movements = new HashMap<>();
        for (Entry entry : snap.rows) {
            movements.computeIfAbsent(entry.schemeId(), id -> new Movement()).add(entry);
        }
        return movements;
    }

    /** Cost / market / units of every AMC, derived from the ledger (MFR_V9 5). */
    private static Map<Long, Sheet> derivedByAmc(Snapshot snap) {
        Map<Long, Sheet> byAmc = new LinkedHashMap<>();
        for (Map.Entry<Long, Movement> entry : movementsByScheme(snap).entrySet()) {
            Movement movement = entry.getValue();
            Sheet sheet = new Sheet(movement.amount, marketValue(movement, null),
                    movement.units, movement.count);
            byAmc.merge(snap.amcOfScheme(entry.getKey()), sheet, Sheet::plus);
        }
        return byAmc;
    }

    private Map<Long, MfFolioSchemeValuation> latestValuations(Set<Long> schemeIds) {
        Map<Long, MfFolioSchemeValuation> latest = new HashMap<>();
        if (schemeIds.isEmpty()) {
            return latest;
        }
        for (MfFolioSchemeValuation valuation : valuations.findBySchemeIdIn(schemeIds)) {
            latest.merge(valuation.getSchemeId(), valuation,
                    (left, right) -> Comparator.nullsFirst(Comparator
                            .comparing(MfFolioSchemeValuation::getValueDate))
                            .compare(left, right) >= 0 ? left : right);
        }
        return latest;
    }

    // ----------------------------------------------------------- responses --
    private static Map<String, Object> row(Long amcId, String amcName, Sheet sheet, Long txnCount) {
        return Map.ofEntries(
                Map.entry("amcId", nullSafe(amcId)),
                Map.entry("amc", nullSafe(amcName)),
                Map.entry("costValue", sheet.cost()),
                Map.entry("marketValue", sheet.market()),
                Map.entry("units", sheet.units() == null ? "" : sheet.units()),
                Map.entry("txnCount", txnCount == null ? "" : txnCount));
    }

    private static Map<String, Object> txnRow(Snapshot snap, Entry entry) {
        MfFolioSchemeTransaction txn = entry.txn();
        MfFolio folio = snap.folioOfScheme(txn.getSchemeId());
        Long amcId = snap.amcOfScheme(txn.getSchemeId());
        return Map.ofEntries(
                Map.entry("id", txn.getId()),
                Map.entry("schemeId", txn.getSchemeId()),
                Map.entry("scheme", nullSafe(snap.schemeName(txn.getSchemeId()))),
                Map.entry("amcId", nullSafe(amcId)),
                Map.entry("amc", nullSafe(snap.amcName(amcId))),
                Map.entry("folioNo", nullSafe(folio == null ? null : folio.getFolioNo())),
                Map.entry("date", nullSafe(txn.getTxnDate() == null ? null : txn.getTxnDate().toString())),
                Map.entry("type", nullSafe(txn.getType())),
                Map.entry("description", nullSafe(txn.getDescription())),
                Map.entry("units", nullSafe(txn.getUnits())),
                Map.entry("nav", nullSafe(txn.getNav())),
                Map.entry("amount", entry.amount()),
                Map.entry("balance", nullSafe(txn.getBalance())));
    }

    /** AMC choices of the filter bars; summary-only AMCs are included too. */
    private static List<Map<String, Object>> amcOptions(Snapshot snap,
            List<MfPortfolioSummary> accepted) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (MfFolio folio : snap.folios.values()) {
            if (folio.getAmcId() != null) {
                names.putIfAbsent(folio.getAmcId(), snap.amcName(folio.getAmcId()));
            }
        }
        for (MfPortfolioSummary row : accepted) {
            if (row.getAmcId() != null) {
                names.putIfAbsent(row.getAmcId(), row.getAmc());
            }
        }
        return names.entrySet().stream()
                .sorted(Comparator.comparing(entry -> Objects.toString(entry.getValue(), ""),
                        String.CASE_INSENSITIVE_ORDER))
                .<Map<String, Object>>map(entry -> Map.of("id", entry.getKey(),
                        "name", nullSafe(entry.getValue())))
                .toList();
    }

    private static Map<String, Object> pagination(int page, int size, int total) {
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        return Map.of("page", page, "size", size, "total", total,
                "from", from, "to", to, "hasMore", to < total);
    }

    private static <T> List<T> pageOf(List<T> rows, int page, int size) {
        int start = Math.min((page - 1) * size, rows.size());
        return rows.subList(start, Math.min(start + size, rows.size()));
    }

    private static int pageSize(int size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private static int pageNumber(int page) {
        return page <= 0 ? 1 : page;
    }

    // -------------------------------------------------------------- values --
    private static BigDecimal marketValue(Movement movement, MfFolioSchemeValuation valuation) {
        BigDecimal nav = navOf(movement, valuation);
        return nav == null ? BigDecimal.ZERO : movement.units.multiply(nav);
    }

    /** Last NAV printed inside the range, else the accepted valuation's NAV. */
    private static BigDecimal navOf(Movement movement, MfFolioSchemeValuation valuation) {
        return movement.nav != null ? movement.nav : (valuation == null ? null : valuation.getNav());
    }

    private static LocalDate navDateOf(Movement movement, MfFolioSchemeValuation valuation) {
        return movement.navDate != null ? movement.navDate
                : (valuation == null ? null : valuation.getValueDate());
    }

    private static boolean isRanged(LocalDate from, LocalDate to) {
        return from != null || to != null;
    }

    private static String text(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private static BigDecimal nullSafeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Object nullSafe(Object value) {
        return value == null ? "" : value;
    }

    // --------------------------------------------------------------- types --
    /** One scheme's (or one AMC's) movements inside the range. */
    private static final class Movement {
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal units = BigDecimal.ZERO;
        private BigDecimal nav;
        private LocalDate navDate;
        private long count;

        /** Rows are walked newest first, so the first NAV found is the latest one. */
        void add(Entry entry) {
            MfFolioSchemeTransaction txn = entry.txn();
            count++;
            amount = amount.add(entry.amount());
            units = units.add(nullSafeAmount(txn.getUnits()));
            if (nav == null && txn.getNav() != null) {
                nav = txn.getNav();
                navDate = txn.getTxnDate();
            }
        }
    }

    /**
     * One ledger line as the reports show it: the stored row and the amount it
     * carries, which is its own amount plus any stamp duty folded into it
     * (MFR_V10 2).
     */
    private record Entry(MfFolioSchemeTransaction txn, BigDecimal amount) {
        Long schemeId() {
            return txn.getSchemeId();
        }
    }

    /** A ledger day of one scheme: the rows a stamp duty line may belong to. */
    private record Key(Long schemeId, LocalDate date) {
    }

    private record Sheet(BigDecimal cost, BigDecimal market, BigDecimal units, long count) {
        private static final Sheet EMPTY = new Sheet(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

        Sheet plus(Sheet other) {
            return new Sheet(cost.add(other.cost), market.add(other.market),
                    units == null || other.units == null ? null : units.add(other.units),
                    count + other.count);
        }
    }

    /** The owner's portfolio in memory: one read per request, no lazy N+1. */
    private static final class Snapshot {
        private final Map<Long, MfFolio> folios = new HashMap<>();
        private final Map<Long, MfFolioScheme> schemes = new HashMap<>();
        private final Map<Long, MfAmc> amcs = new HashMap<>();
        /** The ledger with every stamp duty row already folded (MFR_V10 2). */
        private final List<Entry> rows = new ArrayList<>();

        MfFolioScheme scheme(Long schemeId) {
            return schemes.get(schemeId);
        }

        MfFolio folioOfScheme(Long schemeId) {
            MfFolioScheme scheme = schemes.get(schemeId);
            return scheme == null ? null : folios.get(scheme.getFolioId());
        }

        Long amcOfScheme(Long schemeId) {
            MfFolio folio = folioOfScheme(schemeId);
            return folio == null ? null : folio.getAmcId();
        }

        String amcName(Long amcId) {
            MfAmc amc = amcId == null ? null : amcs.get(amcId);
            return amc == null ? null : amc.getName();
        }

        String schemeName(Long schemeId) {
            MfFolioScheme scheme = schemes.get(schemeId);
            return scheme == null ? null : scheme.getScheme();
        }

        List<MfFolioScheme> schemesOfAmc(Long amcId) {
            return schemes.values().stream()
                    .filter(scheme -> Objects.equals(amcId, amcOfScheme(scheme.getId())))
                    .toList();
        }

        List<String> schemeNamesOfAmc(Long amcId) {
            return schemesOfAmc(amcId).stream().map(MfFolioScheme::getScheme)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        List<String> allSchemeNames() {
            return schemes.values().stream().map(MfFolioScheme::getScheme)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }
}
