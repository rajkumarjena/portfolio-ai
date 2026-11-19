package com.artech.portfolio.mfsip.service;

import com.artech.portfolio.mfsip.dto.MfBankOption;
import com.artech.portfolio.mfsip.dto.MfSipListResponse;
import com.artech.portfolio.mfsip.dto.MfSipRedeemRequest;
import com.artech.portfolio.mfsip.dto.MfSipRedeemResult;
import com.artech.portfolio.mfsip.dto.MfSipRequest;
import com.artech.portfolio.mfsip.dto.MfSipSchemeDto;
import com.artech.portfolio.mfsip.dto.MfSipView;

import java.util.List;

/**
 * MF.md 1/7/11/12 - the Mutual Fund SIP / investment process. Every method is
 * scoped to the signed-in client (CONSISTENCY.md 6); a client id never comes
 * from a request body.
 */
public interface MfSipService {

    /** The SIP list plus its summary cards. */
    MfSipListResponse list();

    MfSipView get(Long id);

    MfSipView create(MfSipRequest request);

    MfSipView update(Long id, MfSipRequest request);

    /** Pause / resume / cancel / close a registration (MF.md 12). */
    MfSipView changeStatus(Long id, String status);

    /** Sell units, an amount or everything, FIFO cost basis (MF.md 10). */
    MfSipRedeemResult redeem(Long id, MfSipRedeemRequest request);

    /** Execute every due instalment of the signed-in client's SIPs. */
    int runDueSips();

    /** The same execution for every client - the daily 03:00 job. */
    int runDueForAll();

    /** Scheme suggestions of the entry screen (imported schemes + own registrations). */
    List<MfSipSchemeDto> schemeOptions();

    /** The client's bank accounts for the deduction drop-down. */
    List<MfBankOption> bankOptions();
}
