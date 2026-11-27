package com.artech.portfolio.credential.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CRED_V1 - the one row that makes a vault unlockable: the DEK wrapped by the
 * KEK and the Argon2 salt the KEK is derived from.
 *
 * <p>There is deliberately no column for the Master Password, for the KEK or for
 * a plaintext DEK - a stolen copy of this table is useless without the Master
 * Password.</p>
 */
@Entity
@Table(name = "vault_config")
@Data
public class VaultConfig {

    /** CRED_V1: only one row should exist. */
    @Id
    private Long id;

    /** DEK encrypted with the KEK (AES-256-GCM, base64). */
    @Column(name = "encrypted_dek", nullable = false, length = 1024)
    private String encryptedDek;

    /** IV used while encrypting the DEK. */
    @Column(name = "dek_iv", nullable = false, length = 64)
    private String dekIv;

    /** Argon2 salt (base64) - not a secret, but useless without the password. */
    @Column(nullable = false, length = 64)
    private String salt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
