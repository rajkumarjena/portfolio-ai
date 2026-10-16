package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MfTransactionRepository extends JpaRepository<MfTransaction, Long> {

    List<MfTransaction> findByHeaderIdOrderByTxnDateAscIdAsc(Long headerId);

    /** FIFO order for redemptions: oldest transactions first (MF.md 10). */
    List<MfTransaction> findByHeaderIdAndUnitsGreaterThanOrderByTxnDateAscIdAsc(Long headerId, double units);

    /**
     * No-duplicates guarantee on import (MF.md 22). Count-returning on purpose:
     * an existsBy variant would render as "fetch first ? rows only", which the
     * project's SQLiteDialect cannot translate (see ClientRepository notes).
     */
    long countByDedupeKey(String dedupeKey);

    long countByPan(String pan);

    /** Review-all-investments report with the same search criteria as the header view (MF.md 19). */
    @Query("""
            select t from MfTransaction t join MfHeader h on h.id = t.headerId join h.scheme s
            where t.pan = :pan
              and (:from is null or t.txnDate >= :from)
              and (:to is null or t.txnDate <= :to)
              and (:amc is null or lower(s.amcName) = lower(:amc))
              and (:category is null or lower(s.category) = lower(:category))
              and (:headerId is null or t.headerId = :headerId)
              and (:q is null or lower(s.schemeName) like lower(concat('%', :q, '%'))
                               or lower(t.description) like lower(concat('%', :q, '%')))
            order by t.txnDate desc, t.id desc
            """)
    List<MfTransaction> search(@Param("pan") String pan,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("amc") String amc,
                               @Param("category") String category,
                               @Param("headerId") Long headerId,
                               @Param("q") String q);
}
