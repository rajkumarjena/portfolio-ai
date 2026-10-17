package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.Bond;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BondRepository extends JpaRepository<Bond, Long> {

    // Per-user scoping (CONSISTENCY.md 6): the view screen only ever lists the
    // signed-in client's own bonds (ADMIN sees all via the service).
    List<Bond> findByClientId(Long clientId);
}
