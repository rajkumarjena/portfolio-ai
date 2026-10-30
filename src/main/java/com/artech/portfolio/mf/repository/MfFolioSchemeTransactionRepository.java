package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfFolioSchemeTransaction;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Accepted ledger rows (MF.md 7g/10). */
public interface MfFolioSchemeTransactionRepository extends JpaRepository<MfFolioSchemeTransaction, Long> {

    List<MfFolioSchemeTransaction> findByOwnerPan(String ownerPan);

    /**
     * MFR_V11 #3: the whole ledger of one owner scope (the user's PAN or the
     * user plus his family members), loaded in a single query so the widget
     * walks it once instead of running one query per period and per scheme.
     */
    List<MfFolioSchemeTransaction> findByOwnerPanIn(Collection<String> ownerPans);

    List<MfFolioSchemeTransaction> findBySchemeIdIn(Collection<Long> schemeIds);

    List<MfFolioSchemeTransaction> findByOwnerPanAndSchemeIdInOrderByTxnDateDescIdDesc(
            String ownerPan, Collection<Long> schemeIds);

    /**
     * MFR_V9 3-6: the ledger inside one (inclusive) date range, newest first -
     * the newest NAV of a scheme therefore comes first when the caller walks
     * the list. The bounds are never null (callers pass the sentinels), so the
     * query stays a plain index-friendly range scan.
     */
    @Query("select t from MfFolioSchemeTransaction t where t.ownerPan = :pan "
            + "and t.txnDate >= :from and t.txnDate <= :to "
            + "order by t.txnDate desc, t.id desc")
    List<MfFolioSchemeTransaction> findForOwnerBetween(@Param("pan") String pan,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);

    @Modifying
    @Transactional
    @Query("delete from MfFolioSchemeTransaction t where t.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
