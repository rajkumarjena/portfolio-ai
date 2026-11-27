package com.artech.portfolio.credential.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CRED_V1 - an append-only trail of what happened to a credential: CREATE,
 * UPDATE, DELETE, VIEW_PASSWORD, COPY_PASSWORD.
 *
 * <p>It records who and when, never the password, the Master Password, a
 * decrypted value or a key.</p>
 */
@Entity
@Table(name = "credential_audit")
@Data
public class CredentialAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One of the ACTION_* constants of VaultConstants. */
    @Column(name = "action_type", length = 30)
    private String actionType;

    @Column(name = "credential_id")
    private Long credentialId;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    /** PAN of the signed-in user who performed the action. */
    @Column(name = "performed_by", length = 20)
    private String performedBy;
}
