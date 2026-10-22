package com.artech.portfolio.bondsip.service;

import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;

import java.util.List;
import java.util.Map;

/**
 * BONDBP_V9/V10: the Bond SIP process without its transaction table - every
 * due instalment becomes a bond row ("In Progress" / "SIP Investment") that
 * the user completes on the Bond Details Entry screen.
 */
public interface BondSipService {

    /**
     * #2/#3: create a Bond SIP for the signed-in client (Investment Date =
     * today, renamed from Create Date in #3). A past-dated SIP is backfilled
     * on the spot with every instalment due per its frequency (#4).
     */
    BondSipView create(CreateBondSipRequest request);

    /**
     * #1: the lean Bond SIP screen list. Admin/Supervisor see only their own
     * by default; can filter by specific clientId or all=true to see all.
     * Regular users see only their own.
     */
    List<BondSipView> listMine(Long clientIdFilter, boolean all);

    default List<BondSipView> listMine(Long clientIdFilter) {
        return listMine(clientIdFilter, false);
    }

    /** Backward compatible overload. */
    default List<BondSipView> listMine() {
        return listMine(null, false);
    }

    /**
     * BONDBP_V9 #4/#5: create one bond row per due instalment of every active
     * SIP's schedule (weekend dates roll to the next weekday). Idempotent -
     * the nightly 00:00 job, the 00:01 execution, the restart sync, screen
     * backfill and the manual Sync Now button all share this one entry point.
     *
     * BONDBP_V10 #1: the manual Sync Now endpoint is ADMIN-only - use
     * reconcileForCurrentUser() for the user-facing POST and this method for
     * the scheduler/other internal callers.
     */
    int runDueSips();

    /** BONDBP_V10 #1: ADMIN-only wrapper around runDueSips() for Sync Now. */
    int reconcileForCurrentUser();

    /** BONDBP_V8 #5: cancel an existing Bond SIP (cannot be undone). */
    BondSipView cancelSip(Long sipId);

    /**
     * BONDBP_V9 #5: the Bond SIP screen's sync banner for the signed-in client
     * - "Bond SIP Transaction is OK ..." when every due instalment has its
     * bond row, otherwise how many are still pending.
     */
    Map<String, Object> syncStatus();
}
