package com.artech.portfolio.fd.repository;

import com.artech.portfolio.fd.entity.FixedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {

    // Per-user scoping (CONSISTENCY.md 6): the view screen only ever lists the
    // signed-in client's own deposits (ADMIN sees all via the service).
    List<FixedDeposit> findByClientId(Long clientId);
}
