package com.artech.portfolio.creditcard.repository;

import com.artech.portfolio.creditcard.entity.CreditCardPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditCardPurchaseRepository extends JpaRepository<CreditCardPurchase, Long> {

    /** Every purchase of one card, oldest first (MP_V5 bills them by month). */
    List<CreditCardPurchase> findByCardIdOrderByPurchaseDateAsc(Long cardId);

    void deleteByCardId(Long cardId);
}
