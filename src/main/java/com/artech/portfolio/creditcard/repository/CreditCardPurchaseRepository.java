package com.artech.portfolio.creditcard.repository;

import com.artech.portfolio.creditcard.entity.CreditCardPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditCardPurchaseRepository extends JpaRepository<CreditCardPurchase, Long> {

    /** Every purchase of one card, oldest first (MP_V5 bills them by month). */
    List<CreditCardPurchase> findByCardIdOrderByPurchaseDateAsc(Long cardId);

    /**
     * MP_V6 #7: every purchase of the client's cards in one query, so the screen
     * and the planner never issue a query per card.
     */
    List<CreditCardPurchase> findByCard_Client_IdOrderByPurchaseDateAsc(Long clientId);

    void deleteByCardId(Long cardId);
}
