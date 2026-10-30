package com.artech.portfolio.mf.repository.tmp;

import com.artech.portfolio.mf.entity.tmp.MfTmpValuation;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Temporary valuations (MF.md 7e/9/11). */
public interface MfTmpValuationRepository extends JpaRepository<MfTmpValuation, Long> {

    List<MfTmpValuation> findBySchemeIdIn(Collection<Long> schemeIds);

    @Modifying
    @Transactional
    @Query("delete from MfTmpValuation v where v.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
