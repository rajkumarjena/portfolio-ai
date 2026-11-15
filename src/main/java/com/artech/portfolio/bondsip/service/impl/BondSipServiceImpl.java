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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BONDBP_V7/V9/V10 + BOND_V11 - Bond SIP, all rules server-side:
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
 *  #5  The nightly 00:00 reconcile job, the 00:01 execution, the restart sync
 *      and the manual Sync Now button all share the same sync entry point;
 *      syncStatus() powers the screen's "Bond SIP Transaction is OK" banner.
 *
 * BOND_V11:
 *  #1  Flow restructured: every sync works per (client-id, sip-id) - the bond
 *      rows of each owner and SIP are indexed first (#7), then only the missing
 *      frequency periods of that pair are generated.
 *  #2  Any client may sync their own Bond SIPs; an ADMIN may sync his own and
 *      any selected client's SIPs.
 *  #3  "Sync Now" is therefore available to every signed-in client (own SIPs).
 *  #4  An ADMIN's "Sync Now" syncs the clients picked in the multi-select.
 *  #6  Idempotency is period based, not date-string based: a period that
 *      already has a bond row for that client-id + sip-id is never created
 *      again, even when the client edited the record's Interest Payment Start
 *      Date inside that period (6.a) - the schedule simply moves on.
 *
 * BOND_V12:
 *  #2/#3 The SIP date was being carried over backwards: a SIP's "SIP Start
 *      Date" is the actual investment date of every instalment it creates, and
 *      it moves forward with the frequency (10 Sep, 12 Oct, 10 Nov for a
 *      monthly SIP started on 10 Sep). Each generated bond row therefore stores
 *      that instalment's own SIP Start Date as its Investment Date.
 *  #4/#4.c/#4.d A frequency period is skipped when any date of that period
 *      already has a SIP Investment row for the same client-id + sip-id - in
 *      any status, EXECUTED or In Progress.
 *  #4.e Only SIP Investment rows take part in that check; one time investment
 *      records are never compared and behave exactly as before.
 *  #5  The same rule applies to every frequency - for a weekly SIP the week is
 *      the period, so any date of that week blocks only that week.
 */
@Service
public class BondSipServiceImpl implements BondSipService {

    /**
     * BOND_V11 #1/#3: CREATED is included as well - a SIP whose creation-time
     * backfill was missed is repaired by the next sync instead of staying
     * stuck. Generation stays idempotent, so this can never duplicate a row.
     */
    private static final List<String> SYNC_STATUSES = List.of(
            BondSipConstants.STATUS_CREATED,
            BondSipConstants.STATUS_INPROGRESS);

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
        if (generateDueInstalments(saved, LocalDate.now(clock), new ArrayList<>()) > 0) {
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
        Set<String> issuers = new LinkedHashSet<>();
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

    // --------------------------------------------------- #1 list (+ V10 #5)

    @Override
    @Transactional(readOnly = true)
    public List<BondSipView> listMine(List<Long> clientIds, boolean all) {
        Client me = currentUserService.requireClient();
        if (currentUserService.isAdmin() || currentUserService.hasAnyRole("SUPERVISOR")) {
            if (all) {
                return bondSipRepository.findAll().stream()
                        .map(BondSipView::of).toList();
            }
            if (clientIds != null && !clientIds.isEmpty()) {
                // BOND_V11 #5: the multi-select filter picks one or many clients.
                return bondSipRepository.findByClientIdInOrderByIdAsc(clientIds).stream()
                        .map(BondSipView::of).toList();
            }
            // By default for admin/supervisor, show only their own
            return bondSipRepository.findByClientIdOrderByIdAsc(me.getId())
                    .stream().map(BondSipView::of).toList();
        }
        return bondSipRepository.findByClientIdOrderByIdAsc(me.getId())
                .stream().map(BondSipView::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BondSipView> listMine() {
        return listMine(List.of(), false);
    }

    // ------------------------------------------- #4/#5 execute + reconcile

    /**
     * BOND_V11 #2/#3/#4: the manual Sync Now button. A regular client syncs
     * their own SIPs; an ADMIN syncs his own by default, or exactly the clients
     * he selected in the screen's multi-select.
     */
    @Override
    @Transactional
    public int reconcileForCurrentUser(List<Long> clientIds) {
        return syncClients(resolveSyncClients(clientIds));
    }

    @Override
    @Transactional
    public int reconcileForCurrentUser() {
        return reconcileForCurrentUser(List.of());
    }

    /** The scheduler / restart sync path: every client's active SIPs. */
    @Override
    @Transactional
    public int runDueSips() {
        return syncClients(null);
    }

    /**
     * BOND_V11 #2: the clients a sync may touch - the selection of an ADMIN,
     * otherwise (and for an empty selection) the signed-in client only. Any
     * client id sent by a non-admin is ignored.
     */
    private List<Long> resolveSyncClients(List<Long> requestedClientIds) {
        Client me = currentUserService.requireClient();
        if (currentUserService.isAdmin() && requestedClientIds != null && !requestedClientIds.isEmpty()) {
            Set<Long> unique = new LinkedHashSet<>();
            for (Long id : requestedClientIds) {
                if (id != null) {
                    unique.add(id);
                }
            }
            if (!unique.isEmpty()) {
                return new ArrayList<>(unique);
            }
        }
        return List.of(me.getId());
    }

    /**
     * BOND_V11 #1/#7: the restructured sync. Bond rows are indexed by
     * client-id + sip-id first, then every candidate SIP of the requested
     * clients fills in only the frequency periods that pair is missing.
     *
     * @param clientIds the clients to sync, or null for all of them.
     * @return how many NEW bond rows were created.
     */
    private int syncClients(List<Long> clientIds) {
        LocalDate today = LocalDate.now(clock);
        List<BondSip> candidates = clientIds == null
                ? bondSipRepository.findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                        SYNC_STATUSES, today)
                : bondSipRepository.findByClientIdInAndStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
                        clientIds, SYNC_STATUSES, today);
        if (candidates.isEmpty()) {
            return 0;
        }

        // #7: client-id + sip-id -> the bond rows that already exist for it.
        Map<String, List<Bond>> existingByOwnerAndSip = new HashMap<>();
        List<Long> sipIds = candidates.stream()
                .map(BondSip::getId).filter(java.util.Objects::nonNull).toList();
        if (!sipIds.isEmpty()) {
            for (Bond bond : bondRepository.findByBondSipIdIn(sipIds)) {
                existingByOwnerAndSip
                        .computeIfAbsent(ownerSipKey(bond.getClient() == null ? null : bond.getClient().getId(),
                                bond.getBondSipId()), key -> new ArrayList<>())
                        .add(bond);
            }
        }

        int created = 0;
        for (BondSip sip : candidates) {
            List<Bond> existing = existingByOwnerAndSip
                    .computeIfAbsent(ownerSipKey(clientIdOf(sip), sip.getId()), key -> new ArrayList<>());
            int generated = generateDueInstalments(sip, today, existing);
            if (generated > 0 && !BondSipConstants.STATUS_INPROGRESS.equals(sip.getStatus())) {
                sip.setStatus(BondSipConstants.STATUS_INPROGRESS);
                bondSipRepository.save(sip);
            }
            created += generated;
        }
        return created;
    }

    private static Long clientIdOf(BondSip sip) {
        return sip.getClient() == null ? null : sip.getClient().getId();
    }

    private static String ownerSipKey(Long clientId, Long sipId) {
        return clientId + ":" + sipId;
    }

    /**
     * BOND_V12 #3/#4: walk the SIP's frequency schedule up to today (weekend
     * dates roll to the next weekday) and create one bond row per frequency
     * period that the client-id + sip-id pair does not have yet.
     *
     * <p>Example (#4.a/#4.b) - SIP Start Date 2026-09-10, MONTHLY, today
     * 2026-11-15: the periods are September (10 Sep), October (10 Oct, rolled to
     * Mon 12 Oct) and November (10 Nov); each generated bond row stores its own
     * period date as Investment Date.</p>
     *
     * <p>A period counts as covered when any SIP Investment row of that pair has
     * an Investment Date (or Interest Payment Start Date) inside the period - the
     * client may edit the date after executing the record, so matching the exact
     * schedule date alone would recreate an instalment they already accepted.</p>
     *
     * @return how many NEW bond rows were created.
     */
    private int generateDueInstalments(BondSip sip, LocalDate today, List<Bond> existingBonds) {
        String frequency = normalizeFrequency(sip.getFrequency());
        if (frequency == null) {
            frequency = BondSipConstants.FREQUENCY_MONTHLY; // legacy rows predate the column
        }

        int created = 0;
        LocalDate nominal = sip.getSipDate();
        while (nominal != null) {
            LocalDate effective = effectiveSipDate(nominal);
            if (effective.isAfter(today)) {
                break;
            }
            LocalDate nextNominal = nextInstalmentDate(nominal, frequency);
            // #4.c/#4.d/#5: the period is the frequency window - a whole month
            // for MONTHLY, the whole week for WEEKLY. Any existing row inside it
            // means the client already has that instalment: skip to the next one.
            if (!periodCovered(existingBonds, nominal, nextNominal)) {
                Bond instalment = instalmentBond(sip, effective);
                bondRepository.save(instalment);
                existingBonds.add(instalment);
                created++;
            }
            nominal = nextNominal;
        }
        return created;
    }

    /**
     * BOND_V12 #4/#5: does the client-id + sip-id pair already have a bond row
     * inside this frequency period? Any date of the period counts (in any
     * status), and only SIP Investment rows are compared (#4.e).
     */
    static boolean periodCovered(List<Bond> existingBonds, LocalDate periodStart, LocalDate periodEnd) {
        for (Bond bond : existingBonds) {
            if (!BondConstants.INVESTMENT_TYPE_SIP.equals(bond.getInvestmentType())) {
                continue; // #4.e: a one time investment never blocks a SIP period
            }
            if (inPeriod(bond.getInvestmentDate(), periodStart, periodEnd)
                    || inPeriod(bond.getInterestPaymentStartDate(), periodStart, periodEnd)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inPeriod(LocalDate date, LocalDate periodStart, LocalDate periodEnd) {
        return date != null && !date.isBefore(periodStart) && date.isBefore(periodEnd);
    }

    /**
     * BONDBP_V9 #4.b-f + BOND_V12 #2/#3: the bond row a due instalment becomes -
     * mapped columns from the Bond SIP screen, everything else deliberately blank
     * (#4.f). Its Investment Date is this instalment's own SIP Start Date, so the
     * value moves forward with the frequency instead of repeating the SIP's
     * investment date.
     */
    private Bond instalmentBond(BondSip sip, LocalDate effective) {
        Bond instalment = new Bond();
        instalment.setClient(sip.getClient());
        instalment.setBondSipId(sip.getId());
        instalment.setIssuer(resolveConfigId(BondConstants.ISSUER, sip.getIssuer())); // same name on all screens
        instalment.setInvestmentDate(effective);                                      // #2/#3 SIP Start Date
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
    public Map<String, Object> syncStatus(List<Long> clientIds) {
        List<Long> targets = resolveSyncClients(clientIds);
        LocalDate today = LocalDate.now(clock);

        int due = 0;
        int pending = 0;
        for (Long clientId : targets) {
            for (BondSip sip : bondSipRepository.findByClientIdOrderByIdAsc(clientId)) {
                if (!SYNC_STATUSES.contains(sip.getStatus())) {
                    continue;
                }
                List<Bond> existing = bondRepository.findByBondSipId(sip.getId());
                String frequency = normalizeFrequency(sip.getFrequency());
                if (frequency == null) {
                    frequency = BondSipConstants.FREQUENCY_MONTHLY;
                }
                LocalDate nominal = sip.getSipDate();
                while (nominal != null) {
                    if (effectiveSipDate(nominal).isAfter(today)) {
                        break;
                    }
                    LocalDate nextNominal = nextInstalmentDate(nominal, frequency);
                    due++;
                    if (!periodCovered(existing, nominal, nextNominal)) {
                        pending++;
                    }
                    nominal = nextNominal;
                }
            }
        }

        boolean ok = pending == 0;
        String scope = targets.size() > 1 ? " for the selected client(s)" : "";
        String message = ok
                ? "Bond SIP Transaction is OK - all " + due
                    + " instalment(s) due so far" + scope + " are present in Bond Records."
                : pending + " Bond SIP instalment(s)" + scope + " are missing in Bond Records - "
                    + "run Sync Now or wait for the nightly sync (00:00).";
        return Map.of(
                "ok", ok,
                "message", message,
                "due", due,
                "pending", pending);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> syncStatus() {
        return syncStatus(List.of());
    }
}
