package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfFolioScheme;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Accepted scheme holdings (MF.md 7c). */
public interface MfFolioSchemeRepository extends JpaRepository<MfFolioScheme, Long> {

    List<MfFolioScheme> findByOwnerPan(String ownerPan);

    List<MfFolioScheme> findByFolioIdIn(Collection<Long> folioIds);

    @Modifying
    @Transactional
    @Query("delete from MfFolioScheme s where s.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
