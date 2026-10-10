package com.artech.portfolio.bond.service;

import com.artech.portfolio.bond.entity.Bond;

import java.util.List;

public interface BondService {
    Bond updateBond(Long id, Bond bondDetails);

    Bond getBondById(Long id);

    void deleteBond(Long id);

    List<Bond> getAllBonds();

    Bond saveBond(Bond bond);
}
