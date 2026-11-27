package com.artech.portfolio.credential.service;

import com.artech.portfolio.credential.dto.CredentialRequest;
import com.artech.portfolio.credential.dto.CredentialView;
import com.artech.portfolio.credential.dto.VaultStatusView;

import java.util.List;

/**
 * CRED_V1 - the vault's business rules. Ownership, encryption and the audit trail
 * live in the implementation; callers only ever see decrypted values after a
 * successful Master Password verification.
 */
public interface CredentialVaultService {

    /** Initialised? Unlocked? - drives which panel the screen shows. */
    VaultStatusView status();

    /** CRED_V1 "Create Vault": salt -> DEK -> KEK -> wrapped DEK -> one config row. */
    void setup(String masterPassword, String confirmPassword);

    /** CRED_V1 "Unlock Vault": KEK from the stored salt, unwrap the DEK, keep it in memory. */
    void unlock(String masterPassword);

    /** Drop the DEK from memory now. */
    void lock();

    /** Every credential of the signed-in client, passwords masked. */
    List<CredentialView> list();

    /** Add a credential, encrypting its password with the in-memory DEK. */
    CredentialView create(CredentialRequest request);

    /** Update a credential; a blank password keeps the stored ciphertext. */
    CredentialView update(Long id, CredentialRequest request);

    /** Remove a credential and write the DELETE audit entry. */
    void delete(Long id);

    /** Reveal one password after verifying the Master Password again (CRED_V1). */
    String reveal(Long id, String masterPassword);

    /** Copy one password - same verification, audited as COPY_PASSWORD. */
    String copy(Long id, String masterPassword);
}
