package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfSummaryReconciliationLine;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MfSummaryReconciliationRepository extends JpaRepository<MfSummaryReconciliationLine, Long> {
    List<MfSummaryReconciliationLine> findByBatchIdAndOwnerPanOrderById(Long batchId, String ownerPan);

    @Modifying
    @Transactional
    @Query("delete from MfSummaryReconciliationLine r where r.batchId = :batchId and r.ownerPan = :pan")
    int deleteByBatch(@Param("batchId") Long batchId, @Param("pan") String pan);

    @Modifying
    @Transactional
    @Query("delete from MfSummaryReconciliationLine r where r.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
