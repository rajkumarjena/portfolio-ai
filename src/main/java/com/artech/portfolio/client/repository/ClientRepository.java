package com.artech.portfolio.client.repository;

import com.artech.portfolio.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {

    // Lookup by the PAN business identifier.
    // Note: an existsByPan / Optional-returning variant is intentionally avoided -
    // Spring Data renders those with "fetch first ? rows only", which the project's
    // SQLiteDialect cannot translate. A List return type applies no limit clause
    // (same pattern as CashFlowDataRepository.findByBondId) and works fine.
    List<Client> findByPan(String pan);

    // Password reset token lookup - also List-returning to avoid the SQLite
    // "fetch first ? rows only" limitation described above.
    List<Client> findByResetTokenHash(String resetTokenHash);

    // Role lookups (admin bootstrap check) - same List-returning pattern.
    List<Client> findByRole(String role);
}
