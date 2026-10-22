package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.dto.BondPageView;
import com.artech.portfolio.bond.dto.BondRowView;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.service.BondService;
import com.artech.portfolio.bond.util.BondConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.util.ClientConstants;
import com.artech.portfolio.common.controller.AppParamController;
import com.artech.portfolio.common.entity.AppParamConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BondServiceImpl implements BondService {

    /** BONDBP_V9 #9: In Progress records on top, then Investment Date ascending. */
    static final Comparator<Bond> BOND_DISPLAY_ORDER = Comparator
            .comparing((Bond b) -> BondConstants.STATUS_IN_PROGRESS.equals(b.getStatus()) ? 0 : 1)
            .thenComparing(Bond::getInvestmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Bond::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BondRepository bondRepository;
    private final CurrentUserService currentUserService;
    private final AppParamController appParamController;
    private final Clock clock;

    public BondServiceImpl(BondRepository bondRepository,
                           CurrentUserService currentUserService,
                           AppParamController appParamController,
                           Clock clock) {
        this.bondRepository = bondRepository;
        this.currentUserService = currentUserService;
        this.appParamController = appParamController;
        this.clock = clock;
    }

    @Override
    public Bond updateBond(Long id, Bond bondDetails) {
        // BONDBP A.6: a normal USER only has view access; editing is for
        // SUPERVISOR (own bonds) and ADMIN (everything). Enforced here, not
        // just in the UI, so the role ladder holds over the API too (AUTH.md d).
        if (!currentUserService.hasAnyRole(ClientConstants.ROLE_SUPERVISOR,
                ClientConstants.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your role does not allow editing bonds");
        }
        return loadOwned(id).map(bond -> {
            // BONDBP_V9 #10: a matured record is history - only its details may
            // be viewed, never edited (or deleted).
            requireNotMatured(bond, "edit");

            bond.setBondName(bondDetails.getBondName());
            bond.setIssuer(bondDetails.getIssuer());
            bond.setCreditRating(bondDetails.getCreditRating());
            bond.setInvestmentDate(bondDetails.getInvestmentDate());
            bond.setInvestmentAmountPerUnit(bondDetails.getInvestmentAmountPerUnit());
            bond.setFaceValuePerUnit(bondDetails.getFaceValuePerUnit());
            bond.setUnits(bondDetails.getUnits());
            bond.setCouponRate(bondDetails.getCouponRate());
            bond.setCouponType(bondDetails.getCouponType());
            bond.setCouponFrequency(bondDetails.getCouponFrequency());
            bond.setInterestPaymentStartDate(bondDetails.getInterestPaymentStartDate());
            bond.setMaturityDate(bondDetails.getMaturityDate());
            bond.setTypeOfSecurity(bondDetails.getTypeOfSecurity());
            bond.setRepaymentPriority(bondDetails.getRepaymentPriority());
            bond.setStaggeredBond(bondDetails.getStaggeredBond());
            bond.setListing(bondDetails.getListing());
            bond.setNotes(bondDetails.getNotes());
            // owner never changes on update. BONDBP_V9 #7/#8/#11: Status,
            // Investment Type and the SIP link are lifecycle data the disabled
            // screen fields can only display - a request body can never set them.

            // BONDBP_V9 #4.h: a SIP instalment record that was In Progress turns
            // EXECUTED the moment every mandatory attribute has been entered.
            if (BondConstants.STATUS_IN_PROGRESS.equals(bond.getStatus())
                    && isCompleted(bond)) {
                bond.setStatus(BondConstants.STATUS_EXECUTED);
            }

            // Handle relational updates cleanly by clearing old children and merging the new ones
            bond.getCashFlowData().clear();
            if (bondDetails.getCashFlowData() != null) {
                bondDetails.getCashFlowData().forEach(bond::addCashFlow);
            }

            return bondRepository.save(bond);
        }).orElseThrow(() -> new RuntimeException("Bond not found with id " + id));
    }

    @Override
    public Bond getBondById(Long id) {
        return loadOwned(id)
                .orElseThrow(() -> new RuntimeException("Bond not found with id " + id));
    }

    @Override
    public void deleteBond(Long id) {
        // BONDBP A.5: delete is an ADMIN-only action.
        if (!currentUserService.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an administrator can delete bonds");
        }
        Bond bond = loadOwned(id)
                .orElseThrow(() -> new RuntimeException("Bond not found with id " + id));
        // BONDBP_V9 #10: matured records are permanent history - view only.
        requireNotMatured(bond, "delete");
        bondRepository.delete(bond);
    }

    @Override
    public List<Bond> getAllBonds() {
        return scopedBonds(null).stream().sorted(BOND_DISPLAY_ORDER).toList();
    }

    @Override
    public BondPageView listSection(String section, int page, int size, Long clientIdFilter) {
        boolean expiredSection = BondConstants.SECTION_EXPIRED.equalsIgnoreCase(section);
        LocalDate today = LocalDate.now(clock);

        List<Bond> scoped = scopedBonds(clientIdFilter).stream()
                // BONDBP_V9 #9/#11: the section split happens on the server, and
                // a SIP record without a maturity date yet stays in the active
                // table until its details are completed.
                .filter(bond -> expiredSection ? isMatured(bond, today) : !isMatured(bond, today))
                .sorted(BOND_DISPLAY_ORDER)
                .toList();

        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, scoped.size());
        int to = Math.min(from + safeSize, scoped.size());

        Map<String, String> configValues = configuredDisplayValues();
        List<BondRowView> rows = scoped.subList(from, to).stream()
                .map(bond -> BondRowView.of(bond, configValues))
                .toList();
        return BondPageView.of(rows, safePage, safeSize, scoped.size());
    }

    // ------------------------------------------------------------ BONDBP_V9

    /**
     * BONDBP_V9 #4.h: every attribute the Bond Details Entry screen marks
     * mandatory must be present before an In Progress SIP record may become
     * EXECUTED. Pure function - the same rule the form enforces client-side,
     * decided on the server so any future front-end inherits it.
     */
    static boolean isCompleted(Bond bond) {
        return notBlank(bond.getBondName())
                && notBlank(bond.getIssuer())
                && notBlank(bond.getCreditRating())
                && bond.getInvestmentDate() != null
                && bond.getInvestmentAmountPerUnit() > 0
                && bond.getFaceValuePerUnit() > 0
                && bond.getUnits() > 0
                && bond.getCouponRate() > 0
                && notBlank(bond.getCouponType())
                && notBlank(bond.getCouponFrequency())
                && bond.getInterestPaymentStartDate() != null
                && bond.getMaturityDate() != null
                && notBlank(bond.getTypeOfSecurity())
                && notBlank(bond.getRepaymentPriority())
                && notBlank(bond.getStaggeredBond());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** BONDBP_V9 #9: the expired / matured section is everything past its maturity date. */
    private boolean isMatured(Bond bond, LocalDate today) {
        return bond.getMaturityDate() != null && bond.getMaturityDate().isBefore(today);
    }

    private void requireNotMatured(Bond bond, String action) {
        if (isMatured(bond, LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A matured bond is history and can only be viewed - it cannot be "
                            + action + "ed. Its details remain available on the Bond "
                            + "Details Entry screen in view-only mode.");
        }
    }

    /**
     * The signed-in client's bonds (ADMIN sees everything, or the explicit
     * filter a privileged user picked on the screen); CONSISTENCY.md 6 scope.
     */
    private List<Bond> scopedBonds(Long clientIdFilter) {
        if (currentUserService.isAdmin()) {
            if (clientIdFilter != null) {
                return bondRepository.findByClientId(clientIdFilter);
            }
            return bondRepository.findAll();
        }
        return bondRepository.findByClientId(currentUserService.requireClient().getId());
    }

    /**
     * BONDBP_V9 #11/#12: display values for the config-backed dropdowns, so the
     * list screen receives ready-to-render text instead of app_param_config ids.
     */
    private Map<String, String> configuredDisplayValues() {
        Map<String, String> values = new HashMap<>();
        for (AppParamConfig row : appParamController.getConfig()) {
            if (row == null || row.getId() == null || row.getParamValue() == null) {
                continue;
            }
            String type = row.getInvestmentType() == null ? "" : row.getInvestmentType().trim();
            if (!BondConstants.BOND.equalsIgnoreCase(type)) {
                continue;
            }
            String name = row.getParamName() == null ? "" : row.getParamName().trim();
            if (BondConstants.ISSUER.equalsIgnoreCase(name)
                    || BondConstants.CREDIT_RATING.equalsIgnoreCase(name)) {
                values.put(String.valueOf(row.getId()), row.getParamValue());
            }
        }
        return values;
    }

    /**
     * Loads a bond and enforces ownership (CONSISTENCY.md 6): a non-admin user
     * gets the "not found" outcome for anyone else's bond so ids cannot be
     * probed across clients.
     */
    private Optional<Bond> loadOwned(Long id) {
        Bond bond = bondRepository.findById(id).orElse(null);
        if (bond == null) {
            return Optional.empty();
        }
        if (currentUserService.isAdmin()) {
            return Optional.of(bond);
        }
        Client me = currentUserService.requireClient();
        return bond.getClient() != null && bond.getClient().getId().equals(me.getId())
                ? Optional.of(bond) : Optional.empty();
    }

    @Override
    public Bond saveBond(Bond bond) {
        // Crucial Mapping Hook: Explicitly bind the parent instance to every incoming payload
        // child element
        if (bond.getCashFlowData() != null) {
            bond.getCashFlowData().forEach(cashFlow -> cashFlow.setBond(bond));
        }
        // CONSISTENCY.md 6: the logged-in client owns the row; any client id in
        // the request body is ignored.
        bond.setId(null);
        bond.setClient(currentUserService.requireClient());
        // BONDBP_V9 #6/#11: a direct entry is always One Time Investment with
        // status EXECUTED - lifecycle fields never come from the body.
        bond.setInvestmentType(BondConstants.INVESTMENT_TYPE_ONE_TIME);
        bond.setStatus(BondConstants.STATUS_EXECUTED);
        bond.setBondSipId(null);
        return bondRepository.save(bond);
    }
}
