package com.artech.portfolio.bondsip.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bondsip.dto.BondSipBondOption;
import com.artech.portfolio.bondsip.dto.BondSipTransactionView;
import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.dto.UpdateBondSipTransactionRequest;
import com.artech.portfolio.bondsip.entity.BondSip;
import com.artech.portfolio.bondsip.entity.BondSipTransaction;
import com.artech.portfolio.bondsip.repository.BondSipRepository;
import com.artech.portfolio.bondsip.repository.BondSipTransactionRepository;
import com.artech.portfolio.bondsip.service.BondSipService;
import com.artech.portfolio.bondsip.util.BondSipConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientFamilyMember;
import com.artech.portfolio.common.controller.AppParamController;
import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.bond.util.BondConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BONDBP_V7 - Bond SIP, all rules server-side (#5 of the series' convention):
 *
 *  #2  Issuer / SIP Date / SIP Amount are mandatory; Create Date is stamped
 *      from the clock (the form field is disabled), Status is always CREATED.
 *      The Issuer is configurable: it must come from the bond issuers
 *      configured in app_param_config (param name: issuer), served through the
 *      cached App Config object that refreshes every app.config.refreshRateDuration
 *      (15 minutes by default) - the same list the Bond screen's Issuer
 *      drop-down uses (/api/bonds/issuer).
 *  #3  Status is never client-supplied.
 *  #4  The scheduler executes due SIPs: on a Mon-Fri SIP date the status turns
 *      INPROGRESS on that date; a weekend date rolls to the next weekday
 *      (Sat -> Mon, Sun -> Mon). Execution creates a Bond SIP Transaction
 *      record with status INPROGRESS holding the immutable SIP snapshot.
 *      Instalments follow the SIP's Frequency (like a bond's coupon
 *      frequency): WEEKLY / MONTHLY / QUARTERLY / HALF-YEARLY / YEARLY. Every
 *      due instalment date creates one transaction - and a SIP entered with a
 *      past SIP Date is backfilled on the spot with every instalment due from
 *      that date up to today, all INPROGRESS on the transaction screen for the
 *      user to complete (#5/#7). Generation is idempotent: an instalment whose
 *      effective (weekday-adjusted) date already has a transaction is skipped.
 *  #5  On the transaction screen only Actual SIP Amount may be entered.
 *  #6  The Bond Name dropdown lists the signed-in client's and his/her family
 *      members' bonds, labelled "Bond Name|PAN"; duplicated (owner, bond
 *      name) pairs collapse to their first occurrence.
 *  #7  Associating the Bond Name flips the transaction to EXECUTED and
 *      mirrors the Actual SIP Amount back onto the parent SIP.
 */
@Service
public class BondSipServiceImpl implements BondSipService {

    private final BondSipRepository bondSipRepository;
    private final BondSipTransactionRepository transactionRepository;
    private final BondRepository bondRepository;
    private final AppParamController appParamController;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public BondSipServiceImpl(BondSipRepository bondSipRepository,
                              BondSipTransactionRepository transactionRepository,
                              BondRepository bondRepository,
                              AppParamController appParamController,
                              CurrentUserService currentUserService,
                              Clock clock) {
        this.bondSipRepository = bondSipRepository;
        this.transactionRepository = transactionRepository;
        this.bondRepository = bondRepository;
        this.appParamController = appParamController;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    // ------------------------------------------------------------- #2 #3 create

    @Override
    @Transactional
    public BondSipView create(CreateBondSipRequest request) {
        Client me = currentUserService.requireClient();

        String issuer = request.getIssuer() == null ? "" : request.getIssuer().trim();
        if (issuer.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issuer is required");
        }
        // Configurable field: only issuers configured in app_param_config
        // (investment type bond, param name issuer) may be used.
        if (!configuredIssuers().contains(issuer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Issuer is not configured - pick one from Application Parameter "
                            + "Configuration (param name: issuer)");
        }
        if (request.getSipDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SIP Date is required");
        }
        if (request.getSipAmount() == null || request.getSipAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SIP Amount must be greater than 0");
        }
        String frequency = normalizeFrequency(request.getFrequency());
        if (frequency == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    request.getFrequency() == null || request.getFrequency().isBlank()
                            ? "Frequency is required"
                            : "Frequency must be one of "
                                + String.join(", ", BondSipConstants.FREQUENCIES));
        }

        BondSip sip = new BondSip();
        sip.setClient(me);                     // CONSISTENCY.md 6 - session, never body
        sip.setIssuer(issuer);
        sip.setCreateDate(LocalDate.now(clock)); // #2: auto current date
        sip.setSipDate(request.getSipDate());
        sip.setSipAmount(request.getSipAmount());
        sip.setFrequency(frequency);
        sip.setStatus(BondSipConstants.STATUS_CREATED); // #3: always CREATED on create
        BondSip saved = bondSipRepository.save(sip);

        // Backfill: a SIP whose date is in the past immediately generates every
        // instalment already due per its frequency - all INPROGRESS on the
        // Bond SIP Transaction screen (#4).
        if (generateDueInstalments(saved, LocalDate.now(clock)) > 0) {
            saved.setStatus(BondSipConstants.STATUS_INPROGRESS);
            saved = bondSipRepository.save(saved);
        }
        return BondSipView.of(saved, null);
    }

    // ------------------------------------------------------------- #2 configurability

    /**
     * The configured bond issuers straight from the cached App Config object
     * (AppParamController), i.e. app_param_config rows with investment type
     * "bond" and param name "issuer" - the exact list /api/bonds/issuer serves
     * to the Bond and Bond SIP screens. The cache reloads on the schedule in
     * application.yaml (app.config.refreshRateDuration, 15 minutes by default).
     */
    private Set<String> configuredIssuers() {
        Set<String> issuers = new LinkedHashSet<>();
        for (AppParamConfig row : appParamController.getConfig()) {
            if (row == null || row.getParamValue() == null || row.getParamValue().isBlank()) {
                continue;
            }
            String type = row.getInvestmentType() == null ? "" : row.getInvestmentType().trim();
            String name = row.getParamName() == null ? "" : row.getParamName().trim();
            if (BondConstants.BOND.equalsIgnoreCase(type)
                    && BondConstants.ISSUER.equalsIgnoreCase(name)) {
                issuers.add(row.getParamValue().trim());
            }
        }
        return issuers;
    }

    // ------------------------------------------------------------- #1 list

    @Override
    @Transactional(readOnly = true)
    public List<BondSipView> listMine() {
        Client me = currentUserService.requireClient();
        List<BondSip> sips = bondSipRepository.findByClientIdOrderByIdAsc(me.getId());

        // Bond Name is derived from the first transaction of each SIP that has one.
        Map<Long, String> bondNamesBySip = new HashMap<>();
        for (BondSipTransaction tx : transactionRepository.findByBondSipClientIdOrderByIdAsc(me.getId())) {
            Long sipId = tx.getBondSip().getId();
            if (!bondNamesBySip.containsKey(sipId) && tx.getBond() != null) {
                bondNamesBySip.put(sipId, tx.getBond().getBondName());
            }
        }

        List<BondSipView> views = new ArrayList<>();
        for (BondSip sip : sips) {
            views.add(BondSipView.of(sip, bondNamesBySip.get(sip.getId())));
        }
        return views;
    }

    // ------------------------------------------------------------- #5 transactions

    @Override
    @Transactional(readOnly = true)
    public List<BondSipTransactionView> listTransactions() {
        Client me = currentUserService.requireClient();
        List<BondSipTransactionView> views = new ArrayList<>();
        for (BondSipTransaction tx :
                transactionRepository.findByBondSipClientIdOrderByIdAsc(me.getId())) {
            views.add(BondSipTransactionView.of(tx));
        }
        return views;
    }

    // ------------------------------------------------------------- #4 execute

    @Override
    @Transactional
    public int runDueSips() {
        LocalDate today = LocalDate.now(clock);
        int executed = 0;
        // Active SIPs: CREATED (nothing generated yet) and INPROGRESS (recurring
        // - future instalments keep coming due after the first one).
        List<BondSip> dueCandidates = bondSipRepository
                .findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                        List.of(BondSipConstants.STATUS_CREATED, BondSipConstants.STATUS_INPROGRESS),
                        today);
        for (BondSip sip : dueCandidates) {
            int generated = generateDueInstalments(sip, today);
            if (generated > 0 && !BondSipConstants.STATUS_INPROGRESS.equals(sip.getStatus())) {
                sip.setStatus(BondSipConstants.STATUS_INPROGRESS);
                bondSipRepository.save(sip);
            }
            executed += generated;
        }
        return executed;
    }

    /**
     * BONDBP_V7 #4 frequency schedule: walk the SIP date forward by its
     * frequency (plusMonths clamps month ends, e.g. Jan 31 -> Feb 28) and
     * create one INPROGRESS transaction per instalment whose weekday-adjusted
     * date is due on or before today. Instalments that already have a
     * transaction are skipped, so daily runs and manual re-runs never
     * duplicate rows.
     *
     * @return how many NEW transaction records were created.
     */
    private int generateDueInstalments(BondSip sip, LocalDate today) {
        // Legacy rows predate the frequency column - treat them as monthly.
        String frequency = normalizeFrequency(sip.getFrequency());
        if (frequency == null) {
            frequency = BondSipConstants.FREQUENCY_MONTHLY;
        }
        Set<LocalDate> generated = new HashSet<>();
        for (BondSipTransaction existing : transactionRepository.findByBondSipId(sip.getId())) {
            if (existing.getSipDate() != null) {
                generated.add(existing.getSipDate());
            }
        }

        int created = 0;
        LocalDate due = sip.getSipDate();
        while (due != null) {
            // #4: a weekend instalment date executes on the following Monday.
            LocalDate effective = effectiveSipDate(due);
            if (effective.isAfter(today)) {
                break;
            }
            if (generated.add(effective)) {   // idempotency anchor
                BondSipTransaction tx = new BondSipTransaction();
                tx.setBondSip(sip);
                tx.setIssuer(sip.getIssuer());      // immutable snapshot (#5)
                tx.setCreateDate(sip.getCreateDate());
                tx.setSipDate(effective);
                tx.setSipAmount(sip.getSipAmount());
                tx.setStatus(BondSipConstants.STATUS_INPROGRESS);
                transactionRepository.save(tx);
                created++;
            }
            due = nextInstalmentDate(due, frequency);
        }
        return created;
    }

    /** The next nominal instalment date after the given one, by frequency. */
    static LocalDate nextInstalmentDate(LocalDate due, String frequency) {
        return switch (frequency) {
            case BondSipConstants.FREQUENCY_WEEKLY -> due.plusWeeks(1);
            case BondSipConstants.FREQUENCY_QUARTERLY -> due.plusMonths(3);
            case BondSipConstants.FREQUENCY_HALF_YEARLY -> due.plusMonths(6);
            case BondSipConstants.FREQUENCY_YEARLY -> due.plusYears(1);
            default -> due.plusMonths(1);      // MONTHLY (and legacy default)
        };
    }

    /** Canonical frequency constant (case/separator-insensitive), or null. */
    static String normalizeFrequency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase().replaceAll("[ _]+", "-");
        for (String frequency : BondSipConstants.FREQUENCIES) {
            if (frequency.equals(key)) {
                return frequency;
            }
        }
        return null;
    }

    /**
     * BONDBP_V7 #4: Mon-Fri executes on the SIP date itself; a Saturday or
     * Sunday SIP date executes on the following Monday.
     */
    static LocalDate effectiveSipDate(LocalDate sipDate) {
        DayOfWeek day = sipDate.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) {
            return sipDate.plusDays(2);
        }
        if (day == DayOfWeek.SUNDAY) {
            return sipDate.plusDays(1);
        }
        return sipDate;
    }

    // ------------------------------------------------------------- #5 #7 update

    @Override
    @Transactional
    public BondSipTransactionView updateTransaction(Long id, UpdateBondSipTransactionRequest request) {
        Client me = currentUserService.requireClient();
        BondSipTransaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bond SIP transaction not found"));

        Long ownerId = tx.getBondSip().getClient().getId();
        if (!me.getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Bond SIP transaction not found");
        }
        if (BondSipConstants.STATUS_EXECUTED.equals(tx.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bond SIP transaction is already executed");
        }
        if (request.getBondId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bond Name is required to execute the transaction");
        }
        if (request.getActualSipAmount() != null && request.getActualSipAmount() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Actual SIP Amount cannot be negative");
        }

        // #6: the bond must be one of the allowed options (own or family's).
        Bond bond = null;
        for (Bond candidate : allowedBonds(me)) {
            if (request.getBondId().equals(candidate.getId())) {
                bond = candidate;
                break;
            }
        }
        if (bond == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bond not found in your or your family member's portfolio");
        }

        tx.setBond(bond);
        tx.setActualSipAmount(request.getActualSipAmount());
        tx.setStatus(BondSipConstants.STATUS_EXECUTED); // #7
        BondSipTransaction saved = transactionRepository.save(tx);

        // Mirror the entered amount onto the parent SIP screen column (#1/#5).
        if (request.getActualSipAmount() != null) {
            tx.getBondSip().setActualSipAmount(request.getActualSipAmount());
            bondSipRepository.save(tx.getBondSip());
        }
        return BondSipTransactionView.of(saved);
    }

    // ------------------------------------------------------------- #6 options

    @Override
    @Transactional(readOnly = true)
    public List<BondSipBondOption> bondOptions() {
        Client me = currentUserService.requireClient();
        Map<String, Bond> firstPerOwnerAndName = new LinkedHashMap<>();
        for (Bond bond : allowedBonds(me)) {
            if (bond.getBondName() == null || bond.getBondName().isBlank()
                    || bond.getClient() == null) {
                continue;
            }
            // #6: same owner + same bond name -> keep the FIRST occurrence only.
            String key = bond.getClient().getId() + "\u0000" + bond.getBondName();
            firstPerOwnerAndName.putIfAbsent(key, bond);
        }
        List<BondSipBondOption> options = new ArrayList<>();
        for (Bond bond : firstPerOwnerAndName.values()) {
            options.add(new BondSipBondOption(bond.getId(),
                    bond.getBondName() + "|" + bond.getClient().getPan()));
        }
        return options;
    }

    /**
     * BONDBP_V7 #6: the signed-in client's bonds plus those of every client he/
     * she associated as a family member (BONDBP_V5 links), lowest bond id first
     * so "first occurrence" is deterministic.
     */
    private List<Bond> allowedBonds(Client me) {
        List<Long> scopeIds = new ArrayList<>();
        scopeIds.add(me.getId());
        if (me.getFamilyMembers() != null) {
            for (ClientFamilyMember link : me.getFamilyMembers()) {
                Long memberId = link.getMember() == null ? null : link.getMember().getId();
                if (memberId != null && !scopeIds.contains(memberId)) {
                    scopeIds.add(memberId);
                }
            }
        }
        List<Bond> bonds = new ArrayList<>(bondRepository.findByClientIdIn(scopeIds));
        bonds.sort(Comparator.comparing(Bond::getId));
        return bonds;
    }
}
