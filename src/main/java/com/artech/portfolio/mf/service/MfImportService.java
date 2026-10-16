package com.artech.portfolio.mf.service;

import com.artech.portfolio.mf.entity.MfImportBatch;

import java.util.List;
import java.util.Map;

/**
 * CAS import + reconciliation business process (MF.md 14-16, 20, 22, 23, 25):
 * upload -> reconcile (validate, dedupe, remap) -> accept into
 * MfHeader / MfTransaction. Nothing is written to the portfolio until accept.
 */
public interface MfImportService {

    /** Parses an uploaded xlsx/csv/pdf statement into a batch of review lines. */
    Map<String, Object> importFile(String pan, String fileName, byte[] data);

    List<MfImportBatch> listBatches(String pan);

    /** Batch + lines + validation checks + before/after detail report. */
    Map<String, Object> getBatch(Long id, String pan);

    /** Re-runs header/detail level validation (MF.md 15) and colour codes it. */
    Map<String, Object> validate(Long id, String pan);

    /** Points an unregistered-scheme line at a registered scheme (MF.md 20). */
    Map<String, Object> remap(Long id, Long lineId, Long schemeId, String pan);

    /** Commits the reviewed lines into the portfolio (MF.md 16). */
    Map<String, Object> accept(Long id, String pan);

    void discard(Long id, String pan);
}
