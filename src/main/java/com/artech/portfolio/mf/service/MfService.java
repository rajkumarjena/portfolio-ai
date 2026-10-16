package com.artech.portfolio.mf.service;

import com.artech.portfolio.client.entity.ClientBankAccount;
import com.artech.portfolio.mf.entity.MfHeader;
import com.artech.portfolio.mf.entity.MfScheme;
import com.artech.portfolio.mf.entity.MfTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mutual fund business process (MF.md): SIP input/view, auto execution,
 * FIFO redemption and the portfolio reports.
 */
public interface MfService {

    /** Bank dropdown source - the logged-in client's own accounts (MF.md 3). */
    List<ClientBankAccount> getBankAccounts(String pan);

    // ---- scheme master (MF.md 20) ------------------------------------------
    List<MfScheme> getSchemes();

    MfScheme saveScheme(MfScheme scheme);

    // ---- SIP / header CRUD (MF.md 1, 2, 4, 5, 11, 12) ----------------------
    MfHeader saveHeader(MfHeader header, String pan);

    MfHeader updateHeader(Long id, MfHeader details, String pan);

    MfHeader getHeader(Long id, String pan);

    void deleteHeader(Long id, String pan);

    // ---- view screens (MF.md 17, 18, 19) -----------------------------------
    /** All SIP-type registrations of this client, with a summary bar (MF.md 1). */
    Map<String, Object> getSips(String pan);

    Map<String, Object> searchHeaders(String pan, LocalDate from, LocalDate to, String amc,
                                      String category, String status, String q);

    Map<String, Object> searchTransactions(String pan, LocalDate from, LocalDate to, String amc,
                                           String category, Long headerId, String q);

    /** Full detail report behind a header row (MF.md 17). */
    Map<String, Object> getHeaderDetail(Long id, String pan);

    // ---- lifecycle (MF.md 10) ----------------------------------------------
    /** Pause / resume / cancel an active SIP. */
    MfHeader setStatus(Long id, String status, String pan);

    /** Partial or full redemption, FIFO oldest lot first (MF.md 10). */
    Map<String, Object> redeem(Long id, String mode, double value, String pan);

    // ---- scheduler (MF.md 7) -----------------------------------------------
    /** Executes every due instalment of every active SIP; returns count run. */
    int runDueSips();

    List<MfTransaction> getTransactionsFor(Long headerId);
}
