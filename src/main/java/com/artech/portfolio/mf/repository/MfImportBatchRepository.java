package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfImportBatchRepository extends JpaRepository<MfImportBatch, Long> {

    List<MfImportBatch> findByPanOrderByUploadedAtDesc(String pan);

    // List-returning on purpose (SQLiteDialect cannot render "fetch first ...").
    List<MfImportBatch> findByIdAndPan(Long id, String pan);
}
