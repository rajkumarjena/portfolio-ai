package com.artech.portfolio.bondsip.repository;

import com.artech.portfolio.bondsip.entity.BondSip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BondSipRepository extends JpaRepository<BondSip, Long> {

    /** Per-user scoping (CONSISTENCY.md 6): the screen lists the signed-in client's own SIPs. */
    List<BondSip> findByClientIdOrderByIdAsc(Long clientId);

    /** BOND_V11 #5: the multi-select client filter of the Bond SIP screen. */
    List<BondSip> findByClientIdInOrderByIdAsc(Collection<Long> clientIds);

    /**
     * BONDBP_V7 #4 + BOND_V11 #1: active SIPs (CREATED = nothing generated yet,
     * INPROGRESS = recurring) scheduled on or before today. The weekend
     * rollover ("next weekday") is applied per row in the service against the
     * injected clock, so SQLite needs no date arithmetic.
     */
    List<BondSip> findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(List<String> statuses, LocalDate date);

    /** BOND_V11 #4: the same candidates narrowed to the clients being synced. */
    List<BondSip> findByClientIdInAndStatusInAndSipDateLessThanEqualOrderBySipDateAsc(
            Collection<Long> clientIds, List<String> statuses, LocalDate date);
}
