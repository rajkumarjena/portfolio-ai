package com.artech.portfolio.credential.repository;

import com.artech.portfolio.credential.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /** The signed-in client's credentials, alphabetical by application (CRED_V1 listing). */
    List<Credential> findByOwnerPanOrderByAppNameAsc(String ownerPan);

    /**
     * One row scoped to its owner - a foreign credential is simply not found,
     * never decrypted and never deleted (CONSISTENCY.md 6).
     *
     * <p>List-returning on purpose: an Optional/limit variant renders
     * "fetch first ? rows only", which this project's SQLiteDialect cannot
     * translate (see ClientRepository).</p>
     */
    List<Credential> findByIdAndOwnerPan(Long id, String ownerPan);
}
