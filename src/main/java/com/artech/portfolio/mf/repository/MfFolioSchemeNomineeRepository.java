package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfFolioSchemeNominee;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Accepted nominees (MF.md 7d). */
public interface MfFolioSchemeNomineeRepository extends JpaRepository<MfFolioSchemeNominee, Long> {

    List<MfFolioSchemeNominee> findBySchemeIdIn(Collection<Long> schemeIds);

    @Modifying
    @Transactional
    @Query("delete from MfFolioSchemeNominee n where n.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
