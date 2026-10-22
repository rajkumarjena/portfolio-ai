package com.artech.portfolio.bondsip.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.util.BondConstants;
import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.entity.BondSip;
import com.artech.portfolio.bondsip.repository.BondSipRepository;
import com.artech.portfolio.bondsip.service.BondSipService;
import com.artech.portfolio.bondsip.util.BondSipConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.common.controller.AppParamController;
import com.artech.portfolio.common.entity.AppParamConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BONDBP_V7/V9 - Bond SIP, all rules server-side:
 *
 *  #2  Issuer / SIP Date / SIP Amount are mandatory; Investment Date (BONDBP_V9
 *      #3, was Create Date) is stamped from the clock (the form field is
 *      disabled), Status is always CREATED. The Issuer must come from the bond
 *      issuers configured in app_param_config (param name: issuer) - the same
 *      list the Bond screen's Issuer drop-down uses (/api/bonds/issuer).
 *  #3  Status is never client-supplied.
 *  #4  BONDBP_V9: on a due SIP date one BOND row is created directly (no
 *      transaction table any more) with status "In Progress" and Investment
 *      Type "SIP Investment":
 *        - Issuer          = the SIP's issuer (same name on all three screens)
 *        - Investment Date = the SIP's Investment Date (#3)
 *        - Interest Payment Start Date = the (weekday-adjusted) SIP Date (#4.c)
 *        - Investment Amount Per Unit  = the SIP Amount (#4.d)
 *        - Coupon Frequency            = the SIP Frequency (#4.e)
 *        - every other attribute stays blank for the user to complete on the
 *          Bond Details Entry screen (#4.f), which turns the row EXECUTED (#4.h).
 *      Instalments follow the SIP's Frequency; a past SIP Date is backfilled
 *      on the spot up to today. Generation is idempotent: an instalment date
 *      that already has a bond row is skipped.
 *  #5  The nightly 00:00 reconcile job, the 00:01 execution, the restart sync
 *      and the manual Sync Now button all share runDueSips(); syncStatus()
 *      powers the screen's "Bond SIP Transaction is OK" banner.
 */
@Service
public class BondSipServiceImpl implements BondSipService {

    private final BondSipRepository bondSipRepository;
    private final BondRepository bondRepository;
    private final AppParamController appParamController;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public BondSipServiceImpl(BondSipRepository bondSipRepository,
                              BondRepository bondRepository,
                              AppParamController appParamController,
                              CurrentUserService currentUserService,
                              Clock clock) {
        this.bondSipRepository = bondSipRepository;
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
        sip.setClient(me);                       // CONSISTENCY.md 6 - session, never body
        sip.setIssuer(issuer);
        sip.setInvestmentDate(LocalDate.now(clock)); // BONDBP_V9 #3: auto current date
        sip.setSipDate(request.getSipDate());
        sip.setSipAmount(request.getSipAmount());
        sip.setFrequency(frequency);
        sip.setStatus(BondSipConstants.STATUS_CREATED); // #3: always CREATED on create
        BondSip saved = bondSipRepository.save(sip);

        // Backfill: a SIP whose date is in the past immediately generates every
        // instalment already due per its frequency - each one a bond row that is
        // In Progress on the Bond Records screen (#4).
        if (generateDueInstalments(saved, LocalDate.now(clock)) > 0) {
            saved.setStatus(BondSipConstants.STATUS_INPROGRESS);
            saved = bondSipRepository.save(saved);
        }
        return BondSipView.of(saved);
    }

    // ------------------------------------------------------------- #2 configurability

    /** The configured bond issuers (app_param_config, param name: issuer). */
    private List<AppParamConfig> configRows(String paramName) {
        List<AppParamConfig> rows = new ArrayList<>();
        for (AppParamConfig row : appParamController.getConfig()) {
            if (row == null || row.getParamValue() == null || row.getParamValue().isBlank()) {
                continue;
            }
            String type = row.getInvestmentType() == null ? "" : row.getInvestmentType().trim();
            String name = row.getParamName() == null ? "" : row.getParamName().trim();
            if (BondConstants.BOND.equalsIgnoreCase(type) && paramName.equalsIgnoreCase(name)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private Set<String> configuredIssuers() {
        Set<String> issuers = new java.util.LinkedHashSet<>();
        for (AppParamConfig row : configRows(BondConstants.ISSUER)) {
            issuers.add(row.getParamValue().trim());
        }
        return issuers;
    }

    /**
     * BONDBP_V9 #4.b/#11: the Bond screens store the app_param_config row id
     * for the Issuer drop-down, so a SIP's issuer name is mapped to that id -
     * all three screens then show the same name. Legacy/unconfigured issuers
     * are kept as raw text (the screens fall back to showing it as-is).
     */
    private String resolveConfigId(String paramName, String displayValue) {
        for (AppParamConfig row : configRows(paramName)) {
            if (row.getParamValue().trim().equalsIgnoreCase(displayValue)) {
                return String.valueOf(row.getId());
            }
        }
        return displayValue;
    }

    /** BONDBP_V9 #4.e: map a SIP frequency onto the coupon-frequency config value. */
    private String resolveCouponFrequency(String sipFrequency) {
        String key = sipFrequency.toUpperCase().replaceAll("[^A-Z0-9]", "");
        for (AppParamConfig row : configRows(BondConstants.COUPON_FREQUENCY)) {
            String value = row.getParamValue().toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (value.equals(key)) {
                return String.valueOf(row.getId());
            }
        }
        return sipFrequency; // no matching config row - keep the readable text
    }

    // ------------------------------------------------------------- #1 list

    @Override
    @Transactional(readOnly = true)
    public List<BondSipView> listMine() {
        Client me = currentUserService.requireClient();
        return bondSipRepository.findByClientIdOrderByIdAsc(me.getId())
                .stream().map(BondSipView::of).toList();
    }

    // ------------------------------------------- #4/#5 execute + reconcile

    @Override
    @Transactional
    public int runDueSips() {
        LocalDate today = LocalDate.now(clock);
        int created = 0;
        // BONDBP_V8 #2: instalment bond rows are only created for SIPs in
        // INPROGRESS status (a CREATED SIP has nothing due yet - creation-time
        // backfill already handled it).
        List<BondSip> dueCandidates = bondSipRepository
                .findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                        List.of(BondSipConstants.STATUS_INPROGRESS),
                        today);
        for (BondSip sip : dueCandidates) {
            int generated = generateDueInstalments(sip, today);
            if (generated > 0 && !BondSipConstants.STATUS_INPROGRESS.equals(sip.getStatus())) {
                sip.setStatus(BondSipConstants.STATUS_INPROGRESS);
                bondSipRepository.save(sip);
            }
            created += generated;
        }
        return created;
    }

    /**
     * BONDBP_V9 #4: walk the SIP's frequency schedule up to today (weekend
     * dates roll to the next weekday) and create one bond row per instalment
     * that does not have one yet - the idempotency anchor is the bond row's
     * Interest Payment Start Date.
     *
     * @return how many NEW bond rows were created.
     */
    private int generateDueInstalments(BondSip sip, LocalDate today) {
        Set<LocalDate> existing = new HashSet<>();
        for (Bond bond : bondRepository.findByBondSipId(sip.getId())) {
            if (bond.getInterestPaymentStartDate() != null) {
                existing.add(bond.getInterestPaymentStartDate());
            }
        }

        int created = 0;
        for (LocalDate effective : dueEffectiveDates(sip, today)) {
            if (existing.add(effective)) {
                bondRepository.save(instalmentBond(sip, effective));
                created++;
            }
        }
        return created;
    }

    /**
     * BONDBP_V9 #4.b-f: the bond row a due instalment becomes - mapped columns
     * from the Bond SIP screen, everything else deliberately blank (#4.f).
     */
    private Bond instalmentBond(BondSip sip, LocalDate effective) {
        Bond instalment = new Bond();
        instalment.setClient(sip.getClient());
        instalment.setBondSipId(sip.getId());
        instalment.setIssuer(resolveConfigId(BondConstants.ISSUER, sip.getIssuer())); // same name on all screens
        instalment.setInvestmentDate(sip.getInvestmentDate());                        // #3 / #4.b
        instalment.setInvestmentAmountPerUnit(sip.getSipAmount());                    // #4.d
        instalment.setInterestPaymentStartDate(effective);                            // #4.c
        instalment.setCouponFrequency(resolveCouponFrequency(sip.getFrequency()));    // #4.e
        instalment.setStatus(BondConstants.STATUS_IN_PROGRESS);                       // #4.b
        instalment.setInvestmentType(BondConstants.INVESTMENT_TYPE_SIP);              // #4.b / #2
        // #4.f: bond name, face value, units, coupon rate, maturity date, ... stay
        // blank/defaulted for the user to complete on the Bond Details Entry screen.
        return instalment;
    }

    /** Every weekday-adjusted instalment date of the SIP due on or before today. */
    static List<LocalDate> dueEffectiveDates(BondSip sip, LocalDate today) {
        String frequency = normalizeFrequency(sip.getFrequency());
        if (frequency == null) {
            frequency = BondSipConstants.FREQUENCY_MONTHLY; // legacy rows predate the column
        }
        List<LocalDate> due = new ArrayList<>();
        LocalDate next = sip.getSipDate();
        while (next != null) {
            LocalDate effective = effectiveSipDate(next);
            if (effective.isAfter(today)) {
                break;
            }
            due.add(effective);
            next = nextInstalmentDate(next, frequency);
        }
        return due;
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

    // -------------------------------------------------------- BONDBP_V8 cancel

    @Override
    @Transactional
    public BondSipView cancelSip(Long sipId) {
        Client me = currentUserService.requireClient();
        BondSip sip = bondSipRepository.findById(sipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bond SIP not found"));
        if (sip.getClient() == null || !me.getId().equals(sip.getClient().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bond SIP not found");
        }
        if (BondSipConstants.STATUS_CANCELLED.equals(sip.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bond SIP already cancelled");
        }
        if (BondSipConstants.STATUS_EXECUTED.equals(sip.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel an executed Bond SIP");
        }
        sip.setStatus(BondSipConstants.STATUS_CANCELLED);
        return BondSipView.of(bondSipRepository.save(sip));
    }

    // ------------------------------------------------------ BONDBP_V9 #5 sync

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> syncStatus() {
        Client me = currentUserService.requireClient();
        LocalDate today = LocalDate.now(clock);
        List<BondSip> sips = bondSipRepository.findByClientIdOrderByIdAsc(me.getId());

        int due = 0;
        int pending = 0;
        for (BondSip sip : sips) {
            if (BondSipConstants.STATUS_CANCELLED.equals(sip.getStatus())
                    || BondSipConstants.STATUS_EXECUTED.equals(sip.getStatus())
                    || !BondSipConstants.STATUS_INPROGRESS.equals(sip.getStatus())) {
                continue;
            }
            Set<LocalDate> existing = new HashSet<>();
            for (Bond bond : bondRepository.findByBondSipId(sip.getId())) {
                if (bond.getInterestPaymentStartDate() != null) {
                    existing.add(bond.getInterestPaymentStartDate());
                }
            }
            for (LocalDate effective : dueEffectiveDates(sip, today)) {
                due++;
                if (!existing.contains(effective)) {
                    pending++;
                }
            }
        }

        boolean ok = pending == 0;
        String message = ok
                ? "Bond SIP Transaction is OK - all " + due
                    + " instalment(s) due so far are present in Bond Records."
                : pending + " Bond SIP instalment(s) are missing in Bond Records - "
                    + "run Sync Now or wait for the nightly sync (00:00).";
        return Map.of(
                "ok", ok,
                "message", message,
                "due", due,
                "pending", pending);
    }
}
