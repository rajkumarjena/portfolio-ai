package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.service.BondService;
import com.artech.portfolio.client.entity.Client;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BondServiceImpl implements BondService {
    private final BondRepository bondRepository;
    private final CurrentUserService currentUserService;

    public BondServiceImpl(BondRepository bondRepository,
                           CurrentUserService currentUserService) {
        this.bondRepository = bondRepository;
        this.currentUserService = currentUserService;
    }

    @Override
    public Bond updateBond(Long id, Bond bondDetails) {
        return loadOwned(id).map(bond -> {
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
            // owner never changes on update

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
        Bond bond = loadOwned(id)
                .orElseThrow(() -> new RuntimeException("Bond not found with id " + id));
        bondRepository.delete(bond);
    }

    @Override
    public List<Bond> getAllBonds() {
        // ADMIN sees the whole book for dashboard reporting; everyone else only
        // their own rows (CONSISTENCY.md 6).
        if (currentUserService.isAdmin()) {
            return bondRepository.findAll();
        }
        Client me = currentUserService.requireClient();
        return bondRepository.findByClientId(me.getId());
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
        return bondRepository.save(bond);
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
}
