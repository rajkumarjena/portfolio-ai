package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfImportBatchRepository extends JpaRepository<MfImportBatch, Long> {

    List<MfImportBatch> findByPanOrderByUploadedAtDesc(String pan);

    /**
     * The accepted history of a client - the statement periods it covers
     * (MF_V3 2/3/6) are read from here, so a new upload can be told apart from
     * a statement the portfolio already holds.
     */
    List<MfImportBatch> findByPanAndStatusOrderByUploadedAtDesc(String pan, String status);

    // List-returning on purpose (SQLiteDialect cannot render "fetch first ...").
    List<MfImportBatch> findByIdAndPan(Long id, String pan);
}
