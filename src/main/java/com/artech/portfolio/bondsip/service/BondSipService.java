package com.artech.portfolio.bondsip.service;

import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;

import java.util.List;
import java.util.Map;

/**
 * BONDBP_V9/V10 + BOND_V11: the Bond SIP process without its transaction table
 * - every due instalment becomes a bond row ("In Progress" / "SIP Investment")
 * that the user completes on the Bond Details Entry screen.
 */
public interface BondSipService {

    /**
     * #2/#3: create a Bond SIP for the signed-in client (Investment Date =
     * today, renamed from Create Date in #3). A past-dated SIP is backfilled
     * on the spot with every instalment due per its frequency (#4).
     */
    BondSipView create(CreateBondSipRequest request);

    /**
     * BONDBP_V10 #5 + BOND_V11 #5: the Bond SIP screen list. Admin/Supervisor
     * see only their own by default, the selected client ids when the
     * multi-select filter is used, or everything with all=true. Regular users
     * see only their own.
     */
    List<BondSipView> listMine(List<Long> clientIds, boolean all);

    /** Backward compatible overload. */
    default List<BondSipView> listMine() {
        return listMine(List.of(), false);
    }

    /**
     * BONDBP_V9 #4/#5 + BOND_V11 #1: create one bond row per due instalment of
     * every active SIP's schedule (weekend dates roll to the next weekday).
     * Idempotent per client-id + sip-id frequency period - the nightly 00:00
     * job, the 00:01 execution, the restart sync, screen backfill and the
     * manual Sync Now button all share this one entry point.
     */
    int runDueSips();

    /**
     * BOND_V11 #2/#3/#4: Sync Now. A regular client syncs their own Bond SIPs;
     * an ADMIN syncs his own or exactly the clients passed in (the screen's
     * multi-select). Client ids from a non-admin are ignored.
     */
    int reconcileForCurrentUser(List<Long> clientIds);

    /** Sync Now for the signed-in client only. */
    int reconcileForCurrentUser();

    /** BONDBP_V8 #5: cancel an existing Bond SIP (cannot be undone). */
    BondSipView cancelSip(Long sipId);

    /**
     * BONDBP_V9 #5 + BOND_V11 #4: the screen's sync banner for the sync scope -
     * "Bond SIP Transaction is OK ..." when every due instalment has its bond
     * row, otherwise how many are still pending.
     */
    Map<String, Object> syncStatus(List<Long> clientIds);

    /** Banner for the signed-in client only. */
    Map<String, Object> syncStatus();
}
