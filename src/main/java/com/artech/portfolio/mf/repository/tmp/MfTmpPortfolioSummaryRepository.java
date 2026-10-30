package com.artech.portfolio.mf.repository.tmp;

import com.artech.portfolio.mf.entity.tmp.MfTmpPortfolioSummary;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Temporary PORTFOLIO SUMMARY rows (MF.md 7a/9). */
public interface MfTmpPortfolioSummaryRepository extends JpaRepository<MfTmpPortfolioSummary, Long> {

    @Query("select s from MfTmpPortfolioSummary s left join fetch s.amcReference where s.ownerPan = :ownerPan order by s.amcId")
    List<MfTmpPortfolioSummary> findByOwnerPanOrderByAmc(@Param("ownerPan") String ownerPan);

    @Modifying
    @Transactional
    @Query("delete from MfTmpPortfolioSummary s where s.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
