package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfFolioSchemeValuation;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Accepted valuations (MF.md 7e/11). */
public interface MfFolioSchemeValuationRepository extends JpaRepository<MfFolioSchemeValuation, Long> {

    List<MfFolioSchemeValuation> findBySchemeIdIn(Collection<Long> schemeIds);

    /**
     * MFR_V11 #3: every valuation of one owner scope in a single query; the
     * widget keeps the newest NAV per scheme from the result.
     */
    List<MfFolioSchemeValuation> findByOwnerPanIn(Collection<String> ownerPans);

    List<MfFolioSchemeValuation> findByOwnerPanAndSchemeIdIn(String ownerPan, Collection<Long> schemeIds);

    @Modifying
    @Transactional
    @Query("delete from MfFolioSchemeValuation v where v.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
