package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfImportLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfImportLineRepository extends JpaRepository<MfImportLine, Long> {

    List<MfImportLine> findByBatchIdOrderByLineNoAsc(Long batchId);

    long countByBatchIdAndStatus(Long batchId, String status);

    long countByBatchIdAndLineType(Long batchId, String lineType);
}
