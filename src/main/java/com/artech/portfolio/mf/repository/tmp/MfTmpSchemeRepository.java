package com.artech.portfolio.mf.repository.tmp;

import com.artech.portfolio.mf.entity.tmp.MfTmpScheme;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Temporary scheme holdings (MF.md 7c/9). */
public interface MfTmpSchemeRepository extends JpaRepository<MfTmpScheme, Long> {

    List<MfTmpScheme> findByOwnerPan(String ownerPan);

    List<MfTmpScheme> findByFolioIdIn(Collection<Long> folioIds);

    @Modifying
    @Transactional
    @Query("delete from MfTmpScheme s where s.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
