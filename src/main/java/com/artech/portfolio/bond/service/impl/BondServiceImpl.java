package com.artech.portfolio.bond.service.impl;

import com.artech.portfolio.bond.entity.Bond;
import com.artech.portfolio.bond.repository.BondRepository;
import com.artech.portfolio.bond.service.BondService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BondServiceImpl implements BondService {
    private final BondRepository bondRepository;

    public BondServiceImpl(BondRepository bondRepository) {
        this.bondRepository = bondRepository;
    }

    @Override
    public Bond updateBond(Long id, Bond bondDetails) {
        return bondRepository.findById(id).map(bond -> {
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
        return bondRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bond not found with id " + id));
    }

    @Override
    public void deleteBond(Long id) {
        bondRepository.deleteById(id);
    }

    @Override
    public List<Bond> getAllBonds() {
        return bondRepository.findAll();
    }

    @Override
    public Bond saveBond(Bond bond) {
        // Crucial Mapping Hook: Explicitly bind the parent instance to every incoming payload
        // child element
        if (bond.getCashFlowData() != null) {
            bond.getCashFlowData().forEach(cashFlow -> cashFlow.setBond(bond));
        }
        return bondRepository.save(bond);
    }
}
