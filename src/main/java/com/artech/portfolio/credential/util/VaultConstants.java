package com.artech.portfolio.credential.util;

/**
 * CRED_V1 - the numbers the Credential Vault runs on. They are constants, never
 * configuration: a value changed here would make every stored password
 * undecryptable, so the vault is defined by this one class.
 */
public final class VaultConstants {

    private VaultConstants() {
    }

    /** Argon2 salt length stored in vault_config.salt (base64). */
    public static final int SALT_BYTES = 16;

    /** AES-GCM nonce length; 96 bits is the size GCM is specified for. */
    public static final int IV_BYTES = 12;

    /** 256-bit data encryption key (CRED_V1 "Key Size: 256-bit"). */
    public static final int DEK_BYTES = 32;

    /** GCM authentication tag - the integrity half of AES/GCM/NoPadding. */
    public static final int GCM_TAG_BITS = 128;

    // OWASP's minimum Argon2id profile (19 MiB, 2 passes, 1 lane). Deliberately
    // modest: the application also has to stay usable on a small machine, and
    // this is already far above what a plain SHA-256 of the password would give.
    public static final int ARGON2_MEMORY_KIB = 19456;
    public static final int ARGON2_ITERATIONS = 2;
    public static final int ARGON2_PARALLELISM = 1;

    /** A shorter Master Password would weaken the root of trust. */
    public static final int MIN_MASTER_PASSWORD_LENGTH = 8;

    /** CRED_V1 session management: remove the DEK from memory after 10 idle minutes. */
    public static final int IDLE_LOCK_MINUTES = 10;

    /** The single vault_config row. */
    public static final long CONFIG_ID = 1L;

    /** credential_audit.action_type - exactly the actions CRED_V1 lists. */
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_VIEW_PASSWORD = "VIEW_PASSWORD";
    public static final String ACTION_COPY_PASSWORD = "COPY_PASSWORD";
}
