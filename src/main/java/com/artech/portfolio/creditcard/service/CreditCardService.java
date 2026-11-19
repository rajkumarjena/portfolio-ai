package com.artech.portfolio.creditcard.service;

import com.artech.portfolio.creditcard.dto.CreditCardBillRequest;
import com.artech.portfolio.creditcard.dto.CreditCardMonthView;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseRequest;
import com.artech.portfolio.creditcard.dto.CreditCardPurchaseView;
import com.artech.portfolio.creditcard.dto.CreditCardRequest;

import java.time.LocalDate;

/**
 * MPC_V1 - the client's credit cards and their bills. Every card has a billing
 * cycle; the amount planned in a month is the bill of the cycle that ends in
 * that month (or, until the bill is entered, the purchases of the cycle).
 *
 * <p>Everything is scoped to the signed-in client (CONSISTENCY.md 6): a card id
 * coming from the popup is only ever accepted when it belongs to the session's
 * client.</p>
 */
public interface CreditCardService {

    /** Every card with the billing cycle that ends in {@code month} ("yyyy-MM"). */
    CreditCardMonthView month(String month);

    /** Add a card (no id) or edit one - including its billing-cycle day. */
    void saveCard(CreditCardRequest request);

    /** Remove a card with its bills and purchases. */
    void deleteCard(Long cardId);

    /** Enter (or correct) the bill amount of one billing cycle. */
    void saveBill(Long cardId, CreditCardBillRequest request);

    CreditCardPurchaseView addPurchase(Long cardId, CreditCardPurchaseRequest request);

    void deletePurchase(Long purchaseId);

    /**
     * What every card plans in the month - the Monthly Planner's credit card
     * figure. Used by the planner module, so it takes the already-resolved
     * client id instead of asking the session again.
     */
    double monthTotal(Long clientId, LocalDate month);
}
