package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfPortfolioSummary;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Accepted PORTFOLIO SUMMARY rows (MF.md 7a). */
public interface MfPortfolioSummaryRepository extends JpaRepository<MfPortfolioSummary, Long> {

    List<MfPortfolioSummary> findByOwnerPanOrderByAmc(String ownerPan);

    @Modifying
    @Transactional
    @Query("delete from MfPortfolioSummary s where s.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
