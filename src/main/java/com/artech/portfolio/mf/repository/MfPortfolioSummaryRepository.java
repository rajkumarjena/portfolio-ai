package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Accepted PORTFOLIO SUMMARY rows (MF.md 7a). */
public interface MfPortfolioSummaryRepository extends JpaRepository<MfPortfolioSummary, Long> {

    @Query("select s from MfPortfolioSummary s left join fetch s.amcReference where s.ownerPan = :ownerPan order by s.amcId")
    List<MfPortfolioSummary> findByOwnerPanOrderByAmc(@Param("ownerPan") String ownerPan);

    @Query("select s from MfPortfolioSummary s left join fetch s.amcReference where s.ownerPan = :ownerPan and s.amcId = :amcId and s.total = false")
    List<MfPortfolioSummary> findHoldingsByOwnerAndAmc(@Param("ownerPan") String ownerPan,
                                                        @Param("amcId") Long amcId);

    @Query("select s from MfPortfolioSummary s where s.ownerPan = :ownerPan and s.total = true")
    java.util.Optional<MfPortfolioSummary> findTotalByOwner(@Param("ownerPan") String ownerPan);

    /**
     * MFR_V12 #5: the printed "Total" line of every owner in the scope - one
     * row per PAN - so the widget's last row is the statement's own total, not
     * a figure derived from the ledger.
     */
    @Query("select s from MfPortfolioSummary s where s.ownerPan in :ownerPans and s.total = true")
    List<MfPortfolioSummary> findTotalsByOwnerPanIn(@Param("ownerPans") Collection<String> ownerPans);

    @Modifying
    @Transactional
    @Query("delete from MfPortfolioSummary s where s.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
