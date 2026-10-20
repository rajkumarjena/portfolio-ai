package com.artech.portfolio.bondsip.service;

import com.artech.portfolio.bondsip.dto.BondSipBondOption;
import com.artech.portfolio.bondsip.dto.BondSipTransactionView;
import com.artech.portfolio.bondsip.dto.BondSipView;
import com.artech.portfolio.bondsip.dto.CreateBondSipRequest;
import com.artech.portfolio.bondsip.dto.UpdateBondSipTransactionRequest;

import java.util.List;

public interface BondSipService {

    /**
     * #2/#3: create a Bond SIP for the signed-in client (create date = today).
     * A past-dated SIP is backfilled on the spot with every instalment due per
     * its frequency - all INPROGRESS on the transaction screen (#4).
     */
    BondSipView create(CreateBondSipRequest request);

    /** #1: the Bond SIP screen list, scoped to the signed-in client. */
    List<BondSipView> listMine();

    /** #5: the Bond SIP Transaction screen list, scoped to the signed-in client. */
    List<BondSipTransactionView> listTransactions();

    /** #6: Bond Name drop-down - own and family members' bonds, labelled "Bond Name|PAN". */
    List<BondSipBondOption> bondOptions();

    /** #5/#7: enter Actual SIP Amount + associate a Bond Name -> status EXECUTED. */
    BondSipTransactionView updateTransaction(Long id, UpdateBondSipTransactionRequest request);

    /**
     * #4: create one transaction per due instalment of every active SIP's
     * frequency schedule (weekend dates roll to the next weekday); idempotent.
     */
    int runDueSips();
}
