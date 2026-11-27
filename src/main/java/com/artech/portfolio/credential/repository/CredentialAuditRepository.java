package com.artech.portfolio.credential.repository;

import com.artech.portfolio.credential.entity.CredentialAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialAuditRepository extends JpaRepository<CredentialAudit, Long> {
}
