package com.artech.portfolio.bond.service;

import com.artech.portfolio.bond.dto.BondPageView;
import com.artech.portfolio.bond.entity.Bond;

import java.util.List;

public interface BondService {
    Bond updateBond(Long id, Bond bondDetails);

    Bond getBondById(Long id);

    void deleteBond(Long id);

    List<Bond> getAllBonds();

    Bond saveBond(Bond bond);

    /**
     * BONDBP_V9 #9/#11: one page of the Bond Records screen, split and ordered
     * on the server - "In Progress" records first, then Investment Date
     * ascending. section = active (open / undated) or expired (matured).
     */
    BondPageView listSection(String section, int page, int size, Long clientIdFilter);
}
