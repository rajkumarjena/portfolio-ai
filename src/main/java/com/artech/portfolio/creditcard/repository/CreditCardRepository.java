package com.artech.portfolio.creditcard.repository;

import com.artech.portfolio.creditcard.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    List<CreditCard> findByClientIdOrderByNameAsc(Long clientId);

    /** A card name is unique per client, compared without case. */
    Optional<CreditCard> findByClientIdAndNameIgnoreCase(Long clientId, String name);

    /** Ownership in the query itself (CONSISTENCY.md 6) - a card of another client is never found. */
    Optional<CreditCard> findByIdAndClientId(Long id, Long clientId);
}
