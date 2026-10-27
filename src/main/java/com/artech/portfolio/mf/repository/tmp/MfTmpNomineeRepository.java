package com.artech.portfolio.mf.repository.tmp;

import com.artech.portfolio.mf.entity.tmp.MfTmpNominee;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Temporary nominees (MF.md 7d/9). */
public interface MfTmpNomineeRepository extends JpaRepository<MfTmpNominee, Long> {

    List<MfTmpNominee> findBySchemeIdIn(Collection<Long> schemeIds);

    @Modifying
    @Transactional
    @Query("delete from MfTmpNominee n where n.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
