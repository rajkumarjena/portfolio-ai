package com.artech.portfolio.creditcard.repository;

import com.artech.portfolio.creditcard.entity.CreditCardPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CreditCardPurchaseRepository extends JpaRepository<CreditCardPurchase, Long> {

    /** The purchases of one billing cycle, oldest first. */
    List<CreditCardPurchase> findByCardIdAndPurchaseDateBetweenOrderByPurchaseDateAsc(
            Long cardId, LocalDate from, LocalDate to);

    void deleteByCardId(Long cardId);
}
