package com.artech.portfolio.mfsip.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientBankAccount;
import com.artech.portfolio.mf.entity.MfFolioScheme;
import com.artech.portfolio.mf.repository.MfFolioSchemeRepository;
import com.artech.portfolio.mfsip.dto.MfBankOption;
import com.artech.portfolio.mfsip.dto.MfSipListResponse;
import com.artech.portfolio.mfsip.dto.MfSipRedeemRequest;
import com.artech.portfolio.mfsip.dto.MfSipRedeemResult;
import com.artech.portfolio.mfsip.dto.MfSipRequest;
import com.artech.portfolio.mfsip.dto.MfSipSchemeDto;
import com.artech.portfolio.mfsip.dto.MfSipView;
import com.artech.portfolio.mfsip.entity.MfSipRegistration;
import com.artech.portfolio.mfsip.repository.MfSipRegistrationRepository;
import com.artech.portfolio.mfsip.service.MfSipService;
import com.artech.portfolio.mfsip.util.MfSipConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MF.md 1/7/10/11/12 - Mutual Fund SIP / investment registration, all rules
 * server-side:
 *
 *  - a registration is a SIP (period + amount + start/end date) or a one time
 *    purchase; the scheme identity, folio, bank account and notes are free text
 *    because the screen's datalists are suggestions, not a closed list;
 *  - the execution is idempotent: every due date of the period up to today adds
 *    one instalment worth {@code sipAmount}, and {@code lastExecutedOn} records
 *    the latest date executed, so the daily 03:00 job, the restart and the
 *    "Run Due Instalments" button can never double-invest;
 *  - units are only computed when a NAV is known, otherwise the invested amount
 *    is the current value as well - the screen reports what it knows;
 *  - a redemption (units / amount / full) removes the FIFO cost basis and closes
 *    the registration when no units are left;
 *  - ownership always comes from the session (CONSISTENCY.md 6).
 */
@Service
public class MfSipServiceImpl implements MfSipService {

    private final MfSipRegistrationRepository registrationRepository;
    private final MfFolioSchemeRepository schemeRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public MfSipServiceImpl(MfSipRegistrationRepository registrationRepository,
                            MfFolioSchemeRepository schemeRepository,
                            CurrentUserService currentUserService,
                            Clock clock) {
        this.registrationRepository = registrationRepository;
        this.schemeRepository = schemeRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- reading

    @Override
    @Transactional(readOnly = true)
    public MfSipListResponse list() {
        Client me = currentUserService.requireClient();
        List<MfSipRegistration> rows = registrationRepository.findByClientIdOrderByIdAsc(me.getId());

        MfSipListResponse response = new MfSipListResponse();
        double invested = 0;
        double current = 0;
        double units = 0;
        int active = 0;
        for (MfSipRegistration row : rows) {
            response.getRows().add(MfSipView.of(row));
            invested += row.getInvestedValue();
            current += row.getCurrentValue();
            units += row.getUnits();
            if (MfSipConstants.STATUS_ACTIVE.equals(row.getStatus())) {
                active++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", rows.size());
        summary.put("active", active);
        summary.put("invested", round2(invested));
        summary.put("current", round2(current));
        summary.put("units", round4(units));
        summary.put("returns", round2(current - invested));
        response.setSummary(summary);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public MfSipView get(Long id) {
        return MfSipView.of(loadOwned(id));
    }

    // ---------------------------------------------------------------- writing

    @Override
    @Transactional
    public MfSipView create(MfSipRequest request) {
        Client me = currentUserService.requireClient();
        MfSipRegistration registration = new MfSipRegistration();
        registration.setClient(me);                       // CONSISTENCY.md 6
        registration.setStatus(MfSipConstants.STATUS_ACTIVE);
        apply(registration, request, true);
        return MfSipView.of(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public MfSipView update(Long id, MfSipRequest request) {
        MfSipRegistration registration = loadOwned(id);
        apply(registration, request, false);
        return MfSipView.of(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public MfSipView changeStatus(Long id, String status) {
        MfSipRegistration registration = loadOwned(id);
        String target = MfSipConstants.statusOr(status);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown status; use one of " + String.join(", ", MfSipConstants.STATUSES));
        }
        registration.setStatus(target);
        return MfSipView.of(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public MfSipRedeemResult redeem(Long id, MfSipRedeemRequest request) {
        MfSipRegistration registration = loadOwned(id);
        double nav = registration.getNav();
        double held = registration.getUnits();
        if (held <= MfSipConstants.ZERO_UNITS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No units are held for this registration");
        }

        String mode = request == null || request.getMode() == null ? ""
                : request.getMode().trim().toUpperCase();
        double requested = request == null ? 0 : request.getValue();
        double units = switch (mode) {
            case MfSipConstants.REDEEM_FULL -> held;
            case MfSipConstants.REDEEM_UNITS -> requested;
            case MfSipConstants.REDEEM_AMOUNT -> nav > 0 ? requested / nav : 0;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Redeem mode must be UNITS, AMOUNT or FULL");
        };
        units = Math.min(Math.max(units, 0), held);
        if (units <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a value greater than zero");
        }

        double proceeds = nav > 0 ? units * nav : 0;
        double costBasis = registration.getInvestedValue() * (units / held);
        registration.setUnits(round4(held - units));
        registration.setInvestedValue(round2(Math.max(0, registration.getInvestedValue() - costBasis)));
        registration.setCurrentValue(nav > 0
                ? round2(registration.getUnits() * nav)
                : registration.getInvestedValue());
        if (registration.getUnits() <= MfSipConstants.ZERO_UNITS) {
            registration.setUnits(0);
            registration.setCurrentValue(0);
            registration.setStatus(MfSipConstants.STATUS_CLOSED);
        }
        registrationRepository.save(registration);

        MfSipRedeemResult result = new MfSipRedeemResult();
        result.setUnitsSold(round4(units));
        result.setProceeds(round2(proceeds));
        result.setCostBasis(round2(costBasis));
        return result;
    }

    // -------------------------------------------------------------- execution

    @Override
    @Transactional
    public int runDueSips() {
        Client me = currentUserService.requireClient();
        return runDue(registrationRepository.findByClientIdOrderByIdAsc(me.getId()));
    }

    @Override
    @Transactional
    public int runDueForAll() {
        return runDue(registrationRepository.findByStatusAndRegistrationType(
                MfSipConstants.STATUS_ACTIVE, MfSipConstants.TYPE_SIP));
    }

    /** One instalment per due date after the last executed one, up to today. */
    private int runDue(List<MfSipRegistration> registrations) {
        LocalDate today = LocalDate.now(clock);
        int executed = 0;
        for (MfSipRegistration registration : registrations) {
            if (!MfSipConstants.STATUS_ACTIVE.equals(registration.getStatus())
                    || !MfSipConstants.TYPE_SIP.equals(registration.getRegistrationType())
                    || registration.getSipStartDate() == null
                    || registration.getSipAmount() <= 0) {
                continue;
            }
            String sipType = MfSipConstants.sipTypeOr(registration.getSipType());
            LocalDate due = registration.getSipStartDate();
            LocalDate last = registration.getLastExecutedOn();
            boolean changed = false;
            while (due != null && !due.isAfter(today)) {
                if (registration.getSipEndDate() != null
                        && due.isAfter(registration.getSipEndDate())) {
                    break;
                }
                if (last == null || due.isAfter(last)) {
                    executeInstalment(registration);
                    registration.setLastExecutedOn(due);
                    executed++;
                    changed = true;
                }
                due = nextInstalmentDate(due, sipType);
            }
            if (changed) {
                registrationRepository.save(registration);
            }
        }
        return executed;
    }

    private void executeInstalment(MfSipRegistration registration) {
        double amount = registration.getSipAmount();
        registration.setInvestedValue(round2(registration.getInvestedValue() + amount));
        if (registration.getNav() > 0) {
            registration.setUnits(round4(registration.getUnits() + amount / registration.getNav()));
            registration.setCurrentValue(round2(registration.getUnits() * registration.getNav()));
        } else {
            // No NAV yet: the invested amount is the best current value known.
            registration.setCurrentValue(registration.getInvestedValue());
        }
    }

    static LocalDate nextInstalmentDate(LocalDate due, String sipType) {
        return switch (sipType) {
            case MfSipConstants.WEEKLY -> due.plusWeeks(1);
            case MfSipConstants.QUARTERLY -> due.plusMonths(3);
            case MfSipConstants.HALF_YEARLY -> due.plusMonths(6);
            case MfSipConstants.YEARLY -> due.plusYears(1);
            default -> due.plusMonths(1);      // MONTHLY
        };
    }

    // -------------------------------------------------------------- references

    @Override
    @Transactional(readOnly = true)
    public List<MfSipSchemeDto> schemeOptions() {
        Client me = currentUserService.requireClient();
        Map<String, MfSipSchemeDto> options = new LinkedHashMap<>();

        // Schemes already imported from MF Central, if any.
        if (me.getPan() != null) {
            for (MfFolioScheme scheme : schemeRepository.findByOwnerPan(me.getPan())) {
                if (scheme.getScheme() == null || scheme.getScheme().isBlank()) {
                    continue;
                }
                options.putIfAbsent(scheme.getScheme(), MfSipSchemeDto.of(scheme.getScheme(), null, null));
            }
        }
        // Plus everything the client has already registered here.
        for (MfSipRegistration registration : registrationRepository.findByClientIdOrderByIdAsc(me.getId())) {
            if (registration.getSchemeName() == null || registration.getSchemeName().isBlank()) {
                continue;
            }
            options.putIfAbsent(registration.getSchemeName(), MfSipSchemeDto.of(
                    registration.getSchemeName(), registration.getAmcName(), registration.getCategory()));
        }
        return new ArrayList<>(options.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MfBankOption> bankOptions() {
        Client me = currentUserService.requireClient();
        List<MfBankOption> options = new ArrayList<>();
        if (me.getBankAccounts() == null) {
            return options;
        }
        for (ClientBankAccount account : me.getBankAccounts()) {
            MfBankOption option = new MfBankOption();
            option.setBankName(account.getBankName());
            option.setAccountNumber(account.getAccountNumber());
            options.add(option);
        }
        return options;
    }

    // ---------------------------------------------------------------- helpers

    /** MF.md 1-6/11/12: map the payload onto the registration, validating it. */
    private void apply(MfSipRegistration registration, MfSipRequest request, boolean creating) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration details are required");
        }
        MfSipSchemeDto scheme = request.getScheme();
        String schemeName = scheme == null ? "" : trim(scheme.getSchemeName());
        if (schemeName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheme name is required");
        }
        registration.setSchemeName(schemeName);
        registration.setAmcName(trim(scheme.getAmcName()));
        registration.setCategory(trim(scheme.getCategory()));
        registration.setFolioNo(trim(request.getFolioNo()));
        registration.setBankName(trim(request.getBankName()));
        registration.setBankAccountNumber(trim(request.getBankAccountNumber()));
        registration.setNotes(request.getNotes());

        String type = MfSipConstants.registrationTypeOr(request.getRegistrationType());
        registration.setRegistrationType(type);
        double amount = request.getSipAmount() == null ? 0 : request.getSipAmount();
        if (amount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount cannot be negative");
        }

        if (MfSipConstants.TYPE_SIP.equals(type)) {
            if (amount <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "SIP Amount must be greater than 0");
            }
            registration.setSipAmount(amount);
            registration.setSipType(MfSipConstants.sipTypeOr(request.getSipType()));
            int day = request.getSipDate() == null ? 1 : request.getSipDate();
            if (day < MfSipConstants.MIN_SIP_DAY || day > MfSipConstants.MAX_SIP_DAY) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "SIP Date must be a day between 1 and 31");
            }
            registration.setSipDay(day);
            LocalDate start = request.getSipStartDate() == null
                    ? LocalDate.now(clock) : request.getSipStartDate();
            registration.setSipStartDate(start);
            if (request.getSipEndDate() != null && request.getSipEndDate().isBefore(start)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "SIP End Date cannot be before the SIP Start Date");
            }
            registration.setSipEndDate(request.getSipEndDate());
        } else {
            // A one time purchase: no schedule, the amount is what was invested.
            registration.setSipType(null);
            registration.setSipDay(0);
            registration.setSipAmount(amount);
            registration.setSipStartDate(request.getSipStartDate());
            registration.setSipEndDate(null);
            registration.setInvestedValue(round2(amount));
        }

        if (request.getNav() != null) {
            registration.setNav(Math.max(0, request.getNav()));
        }
        recalculateHolding(registration);

        String status = MfSipConstants.statusOr(request.getStatus());
        if (status != null) {
            registration.setStatus(status);
        } else if (creating || registration.getStatus() == null) {
            registration.setStatus(MfSipConstants.STATUS_ACTIVE);
        }
    }

    /** Units and current value follow the known NAV; without one they stay at cost. */
    private void recalculateHolding(MfSipRegistration registration) {
        if (registration.getNav() <= 0) {
            registration.setCurrentValue(registration.getInvestedValue());
            return;
        }
        if (MfSipConstants.TYPE_ONE_TIME.equals(registration.getRegistrationType())) {
            registration.setUnits(round4(registration.getInvestedValue() / registration.getNav()));
        }
        registration.setCurrentValue(round2(registration.getUnits() * registration.getNav()));
    }

    /** Loads a registration and enforces ownership (CONSISTENCY.md 6). */
    private MfSipRegistration loadOwned(Long id) {
        MfSipRegistration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mutual fund registration not found"));
        Client me = currentUserService.requireClient();
        if (registration.getClient() == null
                || !me.getId().equals(registration.getClient().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Mutual fund registration not found");
        }
        return registration;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
