package com.artech.portfolio.creditcard.repository;

import com.artech.portfolio.creditcard.entity.CreditCardBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CreditCardBillRepository extends JpaRepository<CreditCardBill, Long> {

    /** The bill of one card's cycle, when the client has entered it. */
    Optional<CreditCardBill> findByCardIdAndCycleEnd(Long cardId, LocalDate cycleEnd);

    void deleteByCardId(Long cardId);
}
