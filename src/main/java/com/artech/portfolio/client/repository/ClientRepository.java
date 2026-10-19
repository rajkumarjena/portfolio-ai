package com.artech.portfolio.client.repository;

import com.artech.portfolio.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * BONDBP_V5 #4.b: type-ahead behind the "Add Family Member" panel - matches
     * client name OR PAN (case-insensitive), skips the owner himself and any
     * client already associated as his family member, ordered by name. The row
     * count is capped in SQL (LIMIT :max, service passes 20) so the query stays
     * cheap even when the client table grows huge (BONDBP_V5 #5).
     *
     * Deliberately a NATIVE query with an explicit SQLite LIMIT clause:
     * Spring Data's Pageable renders "fetch first ? rows only" through this
     * project's SQLiteDialect, which SQLite cannot parse (SQLITE_ERROR near
     * "fetch") - the same limitation documented above for List-returning
     * queries. Native LIMIT keeps the cap inside the database regardless.
     */
    @Query(value = """
            SELECT c.* FROM client c
            WHERE (LOWER(c.name) LIKE LOWER('%' || :q || '%')
                OR LOWER(c.pan)  LIKE LOWER('%' || :q || '%'))
              AND (:excludeId IS NULL OR c.id <> :excludeId)
              AND NOT EXISTS (SELECT f.id FROM client_family_member f
                              WHERE f.member_client_id = c.id
                                AND (:excludeId IS NULL OR f.client_id = :excludeId))
            ORDER BY c.name ASC
            LIMIT :max
            """, nativeQuery = true)
    List<Client> searchByNameOrPan(@Param("q") String q,
                                   @Param("excludeId") Long excludeId,
                                   @Param("max") int max);
}
