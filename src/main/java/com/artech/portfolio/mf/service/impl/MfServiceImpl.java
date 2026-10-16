package com.artech.portfolio.mf.service.impl;

import com.artech.portfolio.client.entity.ClientBankAccount;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.mf.entity.MfHeader;
import com.artech.portfolio.mf.entity.MfScheme;
import com.artech.portfolio.mf.entity.MfTransaction;
import com.artech.portfolio.mf.repository.MfHeaderRepository;
import com.artech.portfolio.mf.repository.MfSchemeRepository;
import com.artech.portfolio.mf.repository.MfTransactionRepository;
import com.artech.portfolio.mf.service.MfService;
import com.artech.portfolio.mf.util.MfConstants;
import com.artech.portfolio.mf.util.MfUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MfServiceImpl implements MfService {

    private static final double EPS = 1e-6;

    private final MfHeaderRepository headerRepository;
    private final MfTransactionRepository transactionRepository;
    private final MfSchemeRepository schemeRepository;
    private final ClientRepository clientRepository;

    public MfServiceImpl(MfHeaderRepository headerRepository,
                         MfTransactionRepository transactionRepository,
                         MfSchemeRepository schemeRepository,
                         ClientRepository clientRepository) {
        this.headerRepository = headerRepository;
        this.transactionRepository = transactionRepository;
        this.schemeRepository = schemeRepository;
        this.clientRepository = clientRepository;
    }

    // ------------------------------------------------------------------ banks

    @Override
    public List<ClientBankAccount> getBankAccounts(String pan) {
        List<Client> clients = clientRepository.findByPan(pan);
        if (clients.isEmpty()) {
            return List.of();
        }
        return clients.get(0).getBankAccounts();
    }

    // ---------------------------------------------------------------- schemes

    @Override
    public List<MfScheme> getSchemes() {
        return schemeRepository.findAll();
    }

    @Override
    public MfScheme saveScheme(MfScheme scheme) {
        return findOrCreateScheme(scheme.getSchemeName(), scheme.getAmcName(), scheme.getCategory());
    }

    /**
     * Registers the scheme only if it is not already in the master list, so the
     * scheme table stays clean (MF.md 13 / 20).
     */
    MfScheme findOrCreateScheme(String schemeName, String amcName, String category) {
        if (schemeName == null || schemeName.isBlank()) {
            throw new RuntimeException("Scheme name is required");
        }
        String amc = (amcName == null ? "" : amcName).trim();
        List<MfScheme> existing = schemeRepository
                .findBySchemeNameIgnoreCaseAndAmcNameIgnoreCase(schemeName.trim(), amc);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        MfScheme s = new MfScheme();
        s.setSchemeName(schemeName.trim());
        s.setAmcName(amc);
        s.setCategory(category == null ? "" : category.trim());
        return schemeRepository.save(s);
    }

    // ------------------------------------------------------------ header CRUD

    @Override
    public MfHeader saveHeader(MfHeader header, String pan) {
        header.setId(null);
        header.setPan(pan);
        resolveScheme(header);
        validate(header);
        if (header.getStatus() == null || header.getStatus().isBlank()) {
            header.setStatus(MfConstants.STATUS_ACTIVE);
        }
        if (header.getRegistrationType() == null || header.getRegistrationType().isBlank()) {
            header.setRegistrationType(MfConstants.REG_ONE_TIME);
        }
        MfHeader saved = headerRepository.save(header);
        // A brand-new SIP has not run anything yet (MF.md 7)
        if (saved.getRegistrationType().equals(MfConstants.REG_SIP)
                && saved.getLastExecutedDate() == null
                && saved.getSipStartDate() != null) {
            saved.setLastExecutedDate(saved.getSipStartDate().minusDays(1));
            saved = headerRepository.save(saved);
        }
        return saved;
    }

    @Override
    public MfHeader updateHeader(Long id, MfHeader details, String pan) {
        MfHeader h = getHeader(id, pan);
        MfScheme before = h.getScheme();

        h.setFolioNo(details.getFolioNo());
        h.setRegistrationType(details.getRegistrationType());
        h.setSipType(details.getSipType());
        h.setSipAmount(details.getSipAmount());
        h.setSipDate(details.getSipDate());
        h.setSipStartDate(details.getSipStartDate());
        h.setSipEndDate(details.getSipEndDate());
        h.setBankName(details.getBankName());
        h.setBankAccountNumber(details.getBankAccountNumber());
        h.setStatus(details.getStatus());
        h.setNotes(details.getNotes());
        if (details.getNav() > 0) {
            h.setNav(details.getNav());
        }
        // Keep scheme unless the user actually picked another one
        if (details.getScheme() != null && details.getScheme().getId() != null
                && !details.getScheme().getId().equals(before.getId())) {
            h.setScheme(details.getScheme());
        }
        resolveScheme(h);
        validate(h);
        h.setUpdatedAt(LocalDateTime.now());
        return headerRepository.save(h);
    }

    @Override
    public MfHeader getHeader(Long id, String pan) {
        MfHeader h = headerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mutual fund not found with id " + id));
        if (!h.getPan().equalsIgnoreCase(pan)) {
            throw new RuntimeException("Mutual fund not found with id " + id);
        }
        return h;
    }

    @Override
    public void deleteHeader(Long id, String pan) {
        MfHeader h = getHeader(id, pan);
        for (MfTransaction t : transactionRepository.findByHeaderIdOrderByTxnDateAscIdAsc(id)) {
            transactionRepository.delete(t);
        }
        headerRepository.delete(h);
    }

    private void resolveScheme(MfHeader header) {
        MfScheme s = header.getScheme();
        if (s == null) {
            throw new RuntimeException("Scheme is required");
        }
        if (s.getId() != null) {
            header.setScheme(schemeRepository.findById(s.getId())
                    .orElseThrow(() -> new RuntimeException("Unknown scheme id " + s.getId())));
        } else {
            header.setScheme(findOrCreateScheme(s.getSchemeName(), s.getAmcName(), s.getCategory()));
        }
    }

    private void validate(MfHeader h) {
        if (h.getSipStartDate() != null && h.getSipEndDate() != null
                && h.getSipEndDate().isBefore(h.getSipStartDate())) {
            throw new RuntimeException("SIP end date must be on or after start date");
        }
        boolean sip = MfConstants.REG_SIP.equals(h.getRegistrationType());
        if (sip) {
            if (h.getSipType() == null || h.getSipType().isBlank()) {
                throw new RuntimeException("SIP type is required for a SIP registration");
            }
            if (h.getSipAmount() <= 0) {
                throw new RuntimeException("SIP amount must be greater than zero");
            }
            if (h.getSipStartDate() == null) {
                throw new RuntimeException("SIP start date is required");
            }
            if (h.getSipDate() < 1 || h.getSipDate() > 31) {
                throw new RuntimeException("SIP date must be between 1 and 31");
            }
        }
    }

    // ------------------------------------------------------------ view screens

    @Override
    public Map<String, Object> getSips(String pan) {
        List<MfHeader> rows = headerRepository.findByPanAndRegistrationTypeOrderBySipStartDate(
                pan, MfConstants.REG_SIP);
        return Map.of("rows", rows, "summary", summarize(rows));
    }

    @Override
    public Map<String, Object> searchHeaders(String pan, LocalDate from, LocalDate to, String amc,
                                             String category, String status, String q) {
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay();
        List<MfHeader> rows = headerRepository
                .search(pan, fromDt, toDt, blankToNull(amc), blankToNull(category), blankToNull(status), blankToNull(q));
        return Map.of("rows", rows, "summary", summarize(rows));
    }

    @Override
    public Map<String, Object> searchTransactions(String pan, LocalDate from, LocalDate to, String amc,
                                                   String category, Long headerId, String q) {
        List<MfTransaction> txns = transactionRepository.search(
                pan, from, to, blankToNull(amc), blankToNull(category),
                headerId == null || headerId == 0 ? null : headerId, blankToNull(q));

        double invested = 0, units = 0;
        Map<Long, MfHeader> headers = new LinkedHashMap<>();
        for (MfTransaction t : txns) {
            invested += t.getAmount();
            units += t.getUnits();
            headers.computeIfAbsent(t.getHeaderId(), id -> headerRepository.findById(id).orElse(null));
        }
        // Context of the holdings these transactions belong to (MF.md 8)
        double holdingValue = 0, holdingInvested = 0, holdingUnits = 0;
        for (MfHeader h : headers.values()) {
            if (h == null) continue;
            holdingValue += h.getCurrentValue();
            holdingInvested += h.getInvestedValue();
            holdingUnits += h.getUnits();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("txnCount", txns.size());
        summary.put("txnInvested", MfUtil.round(invested, 2));
        summary.put("txnUnits", MfUtil.roundUnits(units));
        summary.put("holdingValue", MfUtil.round(holdingValue, 2));
        summary.put("holdingInvested", MfUtil.round(holdingInvested, 2));
        summary.put("holdingReturns", MfUtil.round(holdingValue - holdingInvested, 2));
        summary.put("holdingUnits", MfUtil.roundUnits(holdingUnits));
        return Map.of("rows", txns, "headers", headers, "summary", summary);
    }

    /** Summary bar on the MF view / portfolio screens (MF.md 18). */
    private Map<String, Object> summarize(List<MfHeader> rows) {
        double invested = 0, current = 0, units = 0;
        int active = 0;
        for (MfHeader h : rows) {
            invested += h.getInvestedValue();
            current += h.getCurrentValue();
            units += h.getUnits();
            if (MfConstants.STATUS_ACTIVE.equals(h.getStatus())) active++;
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("count", rows.size());
        s.put("activeCount", active);
        s.put("invested", MfUtil.round(invested, 2));
        s.put("current", MfUtil.round(current, 2));
        s.put("returns", MfUtil.round(current - invested, 2));
        s.put("units", MfUtil.roundUnits(units));
        return s;
    }

    @Override
    public Map<String, Object> getHeaderDetail(Long id, String pan) {
        MfHeader h = getHeader(id, pan);
        List<MfTransaction> txns = transactionRepository.findByHeaderIdOrderByTxnDateAscIdAsc(id);

        double bought = 0, sold = 0, bUnits = 0, sUnits = 0;
        for (MfTransaction t : txns) {
            if (t.getUnits() >= 0) {
                bought += t.getAmount();
                bUnits += t.getUnits();
            } else {
                sold += -t.getAmount();
                sUnits += -t.getUnits();
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("txnCount", txns.size());
        stats.put("bought", MfUtil.round(bought, 2));
        stats.put("boughtUnits", MfUtil.roundUnits(bUnits));
        stats.put("redeemed", MfUtil.round(sold, 2));
        stats.put("redeemedUnits", MfUtil.roundUnits(sUnits));
        stats.put("invested", MfUtil.round(h.getInvestedValue(), 2));
        stats.put("current", MfUtil.round(h.getCurrentValue(), 2));
        stats.put("returns", MfUtil.round(h.getCurrentValue() - h.getInvestedValue(), 2));
        stats.put("units", MfUtil.roundUnits(h.getUnits()));
        stats.put("nav", h.getNav());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("header", h);
        out.put("transactions", txns);
        out.put("stats", stats);
        return out;
    }

    // --------------------------------------------------------------- lifecycle

    @Override
    public MfHeader setStatus(Long id, String status, String pan) {
        MfHeader h = getHeader(id, pan);
        if (!List.of(MfConstants.STATUS_ACTIVE, MfConstants.STATUS_PAUSED,
                MfConstants.STATUS_CANCELLED).contains(status)) {
            throw new RuntimeException("Unsupported status " + status);
        }
        boolean sip = MfConstants.REG_SIP.equals(h.getRegistrationType());
        if (sip && MfConstants.STATUS_CANCELLED.equals(status)) {
            h.setStatus(MfConstants.STATUS_CANCELLED);
        } else {
            h.setStatus(status);
        }
        // Pausing/resuming skips the paused window instead of backfilling it:
        // move the execution cursor forward as of today (MF.md 7 / 10).
        if (sip && (MfConstants.STATUS_PAUSED.equals(status) || MfConstants.STATUS_ACTIVE.equals(status))) {
            LocalDate today = LocalDate.now();
            if (h.getLastExecutedDate() == null || h.getLastExecutedDate().isBefore(today.minusDays(1))) {
                h.setLastExecutedDate(today);
            }
        }
        h.setUpdatedAt(LocalDateTime.now());
        return headerRepository.save(h);
    }

    /**
     * Redemption (MF.md 10): sell by units, by amount, or the full holding.
     * The oldest lots are consumed first (FIFO) to compute the cost basis that
     * is removed from invested value - standard redemption procedure.
     */
    @Override
    public Map<String, Object> redeem(Long id, String mode, double value, String pan) {
        MfHeader h = getHeader(id, pan);
        if (h.getUnits() <= EPS) {
            throw new RuntimeException("This mutual fund has no units left to redeem");
        }
        double nav = h.getNav();
        if (nav <= 0) {
            // fall back to the latest known NAV from a transaction
            List<MfTransaction> all = transactionRepository.findByHeaderIdOrderByTxnDateAscIdAsc(id);
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i).getNav() > 0) {
                    nav = all.get(i).getNav();
                    break;
                }
            }
        }
        if (nav <= 0) {
            throw new RuntimeException("Cannot redeem: NAV is unknown for this holding");
        }

        double unitsToSell;
        double proceeds;
        switch (mode == null ? MfConstants.REDEEM_FULL : mode) {
            case MfConstants.REDEEM_UNITS -> {
                unitsToSell = value;
                proceeds = MfUtil.round(value * nav, 2);
            }
            case MfConstants.REDEEM_AMOUNT -> {
                if (value <= 0) throw new RuntimeException("Redemption amount must be greater than zero");
                unitsToSell = MfUtil.roundUnits(value / nav);
                proceeds = value;
            }
            default -> { // FULL
                unitsToSell = h.getUnits();
                proceeds = MfUtil.round(h.getUnits() * nav, 2);
                mode = MfConstants.REDEEM_FULL;
            }
        }
        if (unitsToSell <= EPS) {
            throw new RuntimeException("Redemption value is too small to sell any units");
        }
        if (unitsToSell > h.getUnits() + EPS) {
            throw new RuntimeException(String.format(
                    "Cannot redeem %.4f units - only %.4f units are held", unitsToSell, h.getUnits()));
        }

        // FIFO: consume the oldest purchase lots first (MF.md 10)
        double costBasis = 0;
        double remaining = unitsToSell;
        List<MfTransaction> lots = transactionRepository
                .findByHeaderIdAndUnitsGreaterThanOrderByTxnDateAscIdAsc(id, 0);
        for (MfTransaction lot : lots) {
            if (remaining <= EPS) break;
            double take = Math.min(remaining, lot.getUnits());
            if (lot.getUnits() > 0) {
                costBasis += take * (lot.getAmount() / lot.getUnits());
            }
            remaining -= take;
        }

        MfTransaction redemption = new MfTransaction();
        redemption.setHeaderId(id);
        redemption.setPan(h.getPan());
        redemption.setTxnDate(LocalDate.now());
        redemption.setTxnType(MfConstants.TXN_REDEMPTION);
        redemption.setDescription(String.format(
                "Redemption (%s) - FIFO oldest lots first, cost basis %.2f", mode, costBasis));
        redemption.setUnits(-MfUtil.roundUnits(unitsToSell));
        redemption.setNav(nav);
        redemption.setAmount(-proceeds);
        redemption.setSource(MfConstants.SRC_REDEMPTION);
        redemption.setDedupeKey("REDEMPTION|" + id + "|" + System.currentTimeMillis());

        double newUnits = Math.max(0, MfUtil.roundUnits(h.getUnits() - unitsToSell));
        h.setUnits(newUnits);
        h.setInvestedValue(Math.max(0, MfUtil.round(h.getInvestedValue() - costBasis, 2)));
        if (nav > 0) {
            h.setCurrentValue(MfUtil.round(newUnits * nav, 2));
        } else {
            h.setCurrentValue(MfUtil.round(h.getCurrentValue() - proceeds, 2));
        }
        h.setNav(nav);
        if (newUnits <= EPS) {
            h.setStatus(MfConstants.STATUS_CLOSED);
            h.setUnits(0);
            h.setCurrentValue(0);
        }
        h.setUpdatedAt(LocalDateTime.now());

        MfTransaction savedTxn = transactionRepository.save(redemption);
        MfHeader savedHeader = headerRepository.save(h);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("header", savedHeader);
        out.put("transaction", savedTxn);
        out.put("unitsSold", MfUtil.roundUnits(unitsToSell));
        out.put("proceeds", proceeds);
        out.put("costBasis", MfUtil.round(costBasis, 2));
        out.put("nav", nav);
        return out;
    }

    // -------------------------------------------------------------- scheduler

    /**
     * Auto-executes SIP instalments from the start date to the end date with
     * the period gap of the SIP type (MF.md 7). Anchored stepping (start + n
     * periods) keeps the original day-of-month / weekday of the SIP.
     */
    @Override
    @Transactional
    public int runDueSips() {
        LocalDate today = LocalDate.now();
        List<MfHeader> due = headerRepository.findDueActiveSips(today);
        int run = 0;
        for (MfHeader h : due) {
            run += executeDueInstalments(h, today);
        }
        return run;
    }

    private int executeDueInstalments(MfHeader h, LocalDate today) {
        LocalDate lastValid = h.getSipEndDate() == null ? today
                : (h.getSipEndDate().isBefore(today) ? h.getSipEndDate() : today);
        int period = periodUnits(h.getSipType());
        if (period <= 0 || h.getSipStartDate() == null) {
            return 0;
        }
        int executed = 0;
        int k = 0;
        while (k < 5000) {
            LocalDate due = step(h.getSipStartDate(), h.getSipType(), k);
            if (due.isAfter(lastValid)) break;
            if (h.getLastExecutedDate() == null || due.isAfter(h.getLastExecutedDate())) {
                if (executeInstalment(h, due)) executed++;
                h.setLastExecutedDate(due);
            }
            k++;
        }
        if (executed > 0) {
            h.setUpdatedAt(LocalDateTime.now());
            headerRepository.save(h);
        }
        return executed;
    }

    /** One instalment: creates the child transaction and moves the aggregates. */
    private boolean executeInstalment(MfHeader h, LocalDate due) {
        String key = "SIP|" + h.getId() + "|" + due;
        if (transactionRepository.countByDedupeKey(key) > 0) {
            return false;
        }
        double nav = h.getNav();
        double units = nav > 0 ? MfUtil.roundUnits(h.getSipAmount() / nav) : 0;

        MfTransaction t = new MfTransaction();
        t.setHeaderId(h.getId());
        t.setPan(h.getPan());
        t.setTxnDate(due);
        t.setTxnType(MfConstants.TXN_SIP_INSTALMENT);
        t.setDescription("SIP instalment (" + h.getSipType() + ") - auto executed");
        t.setUnits(units);
        t.setNav(nav);
        t.setAmount(h.getSipAmount());
        t.setSource(MfConstants.SRC_SIP);
        t.setDedupeKey(key);
        transactionRepository.save(t);

        h.setInvestedValue(MfUtil.round(h.getInvestedValue() + h.getSipAmount(), 2));
        if (units > 0) {
            h.setUnits(MfUtil.roundUnits(h.getUnits() + units));
        }
        h.setCurrentValue(nav > 0
                ? MfUtil.round(h.getUnits() * nav, 2)
                : MfUtil.round(h.getCurrentValue() + h.getSipAmount(), 2));
        return true;
    }

    private static int periodUnits(String sipType) {
        if (sipType == null) return -1;
        return switch (sipType) {
            case MfConstants.SIP_WEEKLY -> 7;
            case MfConstants.SIP_MONTHLY -> 1;
            case MfConstants.SIP_QUARTERLY -> 3;
            case MfConstants.SIP_HALF_YEARLY -> 6;
            case MfConstants.SIP_YEARLY -> 12;
            default -> -1;
        };
    }

    /** start + k periods, anchored on the original start date. */
    private static LocalDate step(LocalDate start, String sipType, int k) {
        return switch (sipType) {
            case MfConstants.SIP_WEEKLY -> start.plusWeeks(k);
            case MfConstants.SIP_MONTHLY -> start.plusMonths(k);
            case MfConstants.SIP_QUARTERLY -> start.plusMonths(3L * k);
            case MfConstants.SIP_HALF_YEARLY -> start.plusMonths(6L * k);
            case MfConstants.SIP_YEARLY -> start.plusYears(k);
            default -> start.plusMonths(k);
        };
    }

    @Override
    public List<MfTransaction> getTransactionsFor(Long headerId) {
        return transactionRepository.findByHeaderIdOrderByTxnDateAscIdAsc(headerId);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank() || "ALL".equalsIgnoreCase(s)) ? null : s.trim();
    }
}
