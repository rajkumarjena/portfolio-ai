package com.artech.portfolio.mf.service;

import java.util.Map;

/**
 * The five reconciliation reports of MF.md 12, shown before an upload is
 * accepted (MF.md 13):
 *
 * <ol>
 *   <li>1 - transactions the new upload brings that the accepted data has not;</li>
 *   <li>2 - every AMC summary line against the transactions behind it;</li>
 *   <li>3 - ADJUSTED amounts that changed because older transactions arrived;</li>
 *   <li>4 - accepted transactions the new upload no longer carries;</li>
 *   <li>5 - totals of units / cost / market value inside the tolerance.</li>
 * </ol>
 */
public interface MfReconciliationService {

    Map<String, Object> report(Long batchId, String ownerPan);
}
