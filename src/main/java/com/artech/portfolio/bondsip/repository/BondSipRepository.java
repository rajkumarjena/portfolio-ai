package com.artech.portfolio.bondsip.repository;

import com.artech.portfolio.bondsip.entity.BondSip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BondSipRepository extends JpaRepository<BondSip, Long> {

    /** Per-user scoping (CONSISTENCY.md 6): the screen lists the signed-in client's own SIPs. */
    List<BondSip> findByClientIdOrderByIdAsc(Long clientId);

    /**
     * BONDBP_V7 #4 candidates: an active SIP (CREATED = nothing generated yet,
     * INPROGRESS = recurring, more instalments to come) scheduled on or before
     * today. The weekend rollover ("next weekday") is applied per row in the
     * service against the injected clock, so SQLite needs no date arithmetic.
     */
    List<BondSip> findByStatusInAndSipDateLessThanEqualOrderBySipDateAsc(List<String> statuses, LocalDate date);
}
