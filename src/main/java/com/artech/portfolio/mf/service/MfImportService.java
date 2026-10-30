package com.artech.portfolio.mf.service;

import com.artech.portfolio.mf.entity.MfImportBatch;

import java.util.List;
import java.util.Map;

/**
 * KFINTECH CAS upload flow (MF.md 1-3, 9, 16, 17):
 * <pre>
 *   upload (pdf + password) -> parsed into the TEMPORARY table set
 *   reconciliation report    -> read back from temp against the accepted data
 *   accept                   -> temp copied into the PERMANENT table set
 * </pre>
 * Nothing is written before the pdf has been read successfully, so a failed
 * upload leaves the last good staging data in the temporary tables (MF.md 9).
 */
public interface MfImportService {

    /**
     * Parses the uploaded report and refreshes the temporary table set.
     *
     * @param password password of the protected pdf, may be null when the file
     *                 is not encrypted (MF.md 2/3)
     */
    Map<String, Object> importFile(String ownerPan, String fileName, byte[] data, String password);

    /** Import history of the client (MF.md 17). */
    List<MfImportBatch> listBatches(String ownerPan);

    /** The parsed report as JSON, for the high level check (MF.md 16). */
    String parsedJson(Long batchId, String ownerPan);

    /** Commits the reviewed temporary tables into the permanent ones (MF.md 12/13). */
    Map<String, Object> accept(Long batchId, String ownerPan);

    /** Drops an upload that must not reach the permanent tables (MF.md 17). */
    void discard(Long batchId, String ownerPan);
}
