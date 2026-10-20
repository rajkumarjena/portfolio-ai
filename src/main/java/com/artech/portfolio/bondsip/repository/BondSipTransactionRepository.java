package com.artech.portfolio.bondsip.repository;

import com.artech.portfolio.bondsip.entity.BondSipTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BondSipTransactionRepository extends JpaRepository<BondSipTransaction, Long> {

    /** Transactions of the SIPs owned by the given client, oldest first (deterministic screens). */
    List<BondSipTransaction> findByBondSipClientIdOrderByIdAsc(Long clientId);

    /**
     * All instalments already generated for one Bond SIP - the idempotency
     * anchor for the frequency schedule (an instalment whose effective date
     * already has a transaction is never created twice).
     */
    List<BondSipTransaction> findByBondSipId(Long bondSipId);
}
