package com.artech.portfolio.mf.repository.tmp;

import com.artech.portfolio.mf.entity.tmp.MfTmpTransaction;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Temporary ledger rows (MF.md 7g/9/10). */
public interface MfTmpTransactionRepository extends JpaRepository<MfTmpTransaction, Long> {

    List<MfTmpTransaction> findByOwnerPan(String ownerPan);

    List<MfTmpTransaction> findBySchemeIdIn(Collection<Long> schemeIds);

    @Modifying
    @Transactional
    @Query("delete from MfTmpTransaction t where t.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
