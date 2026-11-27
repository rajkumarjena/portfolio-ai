package com.artech.portfolio.credential.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CRED_V1 - one stored application credential.
 *
 * <p>The password is never held here in plaintext: {@link #encryptedPassword} is
 * the AES-256-GCM ciphertext and {@link #passwordIv} the IV it was produced
 * with. Decryption needs the DEK, which exists only in server memory while the
 * vault is unlocked.</p>
 *
 * <p>{@code clientPan} is the PAN this credential is used for - always the
 * signed-in client's own PAN, shown read-only on the screen. {@code ownerPan}
 * is the same client's PAN and scopes every
 * query, so one client can never see or delete another's rows - the same
 * CONSISTENCY.md 6 rule every other module follows.</p>
 */
@Entity
@Table(name = "credentials")
@Data
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CONSISTENCY.md 6: the row belongs to the signed-in client, never to the request body. */
    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    /** CRED_V1: Client PAN - the signed-in client's PAN, taken from the session. */
    @Column(name = "client_pan", nullable = false, length = 20)
    private String clientPan;

    @Column(name = "app_name", nullable = false, length = 120)
    private String appName;

    @Column(nullable = false, length = 160)
    private String username;

    /** AES-256-GCM ciphertext, base64 - never the password itself. */
    @Column(name = "encrypted_password", nullable = false, length = 1024)
    private String encryptedPassword;

    /** Base64 IV of this ciphertext; a new one on every encryption. */
    @Column(name = "password_iv", nullable = false, length = 64)
    private String passwordIv;

    @Column(length = 300)
    private String website;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
