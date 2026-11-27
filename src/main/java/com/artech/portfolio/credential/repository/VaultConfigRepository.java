package com.artech.portfolio.credential.repository;

import com.artech.portfolio.credential.entity.VaultConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The vault configuration is one row. Lookups stay List-returning because this
 * project's SQLiteDialect cannot translate Spring Data's "fetch first ? rows
 * only" that an Optional-returning finder would render (see ClientRepository).
 */
public interface VaultConfigRepository extends JpaRepository<VaultConfig, Long> {
}
