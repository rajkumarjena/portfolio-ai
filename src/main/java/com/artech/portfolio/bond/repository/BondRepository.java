package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.Bond;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BondRepository extends JpaRepository<Bond, Long> {

    // Per-user scoping (CONSISTENCY.md 6): the view screen only ever lists the
    // signed-in client's own bonds (ADMIN sees all via the service).
    List<Bond> findByClientId(Long clientId);

    // BONDBP_V6 #4: the Portfolio Summary widget loads one whole scope (Self =
    // the signed-in client, Family = him/her plus every associated family
    // member) in a single query so both columns are computed server-side (#6).
    List<Bond> findByClientIdIn(Collection<Long> clientIds);
}
