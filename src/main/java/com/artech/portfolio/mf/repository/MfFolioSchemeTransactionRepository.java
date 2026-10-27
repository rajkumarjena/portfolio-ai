package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Accepted ledger rows (MF.md 7g/10). */
public interface MfFolioSchemeTransactionRepository extends JpaRepository<MfFolioSchemeTransaction, Long> {

    List<MfFolioSchemeTransaction> findByOwnerPan(String ownerPan);

    List<MfFolioSchemeTransaction> findBySchemeIdIn(Collection<Long> schemeIds);

    @Modifying
    @Transactional
    @Query("delete from MfFolioSchemeTransaction t where t.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
