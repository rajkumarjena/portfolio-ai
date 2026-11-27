package com.artech.portfolio.creditcard.service;

import com.artech.portfolio.creditcard.dto.CreditCardMonthView;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseRequest;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseView;
import com.artech.portfolio.creditcard.dto.CreditCardRequest;
import com.artech.portfolio.creditcard.dto.CreditCardTransactionRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * MPC_V1 - the client's credit cards and their transactions.
 *
 * <p>MP_V5: a card is a name, a billing-cycle day and a grace period. Every
 * purchase is billed in one month - the month of its cycle end plus the grace
 * period by default, movable between the current and the next month - and the
 * amount the Monthly Planner plans for a month is the sum of the transactions
 * billed in that month.</p>
 *
 * <p>Everything is scoped to the signed-in client (CONSISTENCY.md 6): a card id
 * coming from the popup is only ever accepted when it belongs to the session's
 * client.</p>
 */
public interface CreditCardService {

    /** Every card with its transactions for {@code month} ("yyyy-MM"). */
    CreditCardMonthView month(String month);

    /** Add a card (no id) or edit one - name, cycle day and grace period. */
    void saveCard(CreditCardRequest request);

    /** Remove a card with its transactions. */
    void deleteCard(Long cardId);

    /**
     * MP_V5 #3.iv: save every changed transaction's billed month in one call.
     *
     * <p>MP_V6 #2: {@code month} is the selected month - only its own two months
     * ({@code month} and the one after it) are accepted, and a past month is
     * read only.</p>
     */
    void saveTransactions(String month, List<CreditCardTransactionRequest> transactions);

    CreditCardPurchaseView addPurchase(Long cardId, CreditCardPurchaseRequest request);

    void deletePurchase(Long purchaseId);

    /**
     * What every card plans in the month - the Monthly Planner's credit card
     * figure (MP_V5 #5/#6). Used by the planner module, so it takes the
     * already-resolved client id instead of asking the session again.
     */
    double monthTotal(Long clientId, LocalDate month);
}
