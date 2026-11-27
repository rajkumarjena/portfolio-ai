package com.artech.portfolio.credential.service.impl;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.credential.dto.CredentialRequest;
import com.artech.portfolio.credential.dto.CredentialView;
import com.artech.portfolio.credential.dto.VaultStatusView;
import com.artech.portfolio.credential.entity.Credential;
import com.artech.portfolio.credential.entity.CredentialAudit;
import com.artech.portfolio.credential.entity.VaultConfig;
import com.artech.portfolio.credential.repository.CredentialAuditRepository;
import com.artech.portfolio.credential.repository.CredentialRepository;
import com.artech.portfolio.credential.repository.VaultConfigRepository;
import com.artech.portfolio.credential.service.CredentialVaultService;
import com.artech.portfolio.credential.service.VaultSession;
import com.artech.portfolio.credential.util.VaultConstants;
import com.artech.portfolio.credential.util.VaultCrypto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CRED_V1 - the vault itself.
 *
 * <p>Two keys, never stored: the KEK (Argon2id of the Master Password and the
 * stored salt) only ever exists for the length of one call, and the DEK lives in
 * {@link VaultSession} while the vault is unlocked. Passwords are encrypted with
 * the DEK and a fresh IV each time, and every action is written to
 * credential_audit - without the password, the Master Password, a key or any
 * decrypted value.</p>
 */
@Service
public class CredentialVaultServiceImpl implements CredentialVaultService {

    private static final String WRONG_MASTER = "Master Password is incorrect";

    private final VaultConfigRepository configRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialAuditRepository auditRepository;
    private final VaultSession session;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public CredentialVaultServiceImpl(VaultConfigRepository configRepository,
                                      CredentialRepository credentialRepository,
                                      CredentialAuditRepository auditRepository,
                                      VaultSession session,
                                      CurrentUserService currentUserService,
                                      Clock clock) {
        this.configRepository = configRepository;
        this.credentialRepository = credentialRepository;
        this.auditRepository = auditRepository;
        this.session = session;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Override
    public VaultStatusView status() {
        return new VaultStatusView(config() != null, session.isUnlocked(),
                VaultConstants.IDLE_LOCK_MINUTES);
    }

    @Override
    @Transactional
    public void setup(String masterPassword, String confirmPassword) {
        if (config() != null) {
            throw new IllegalArgumentException(
                    "A vault already exists - unlock it with its Master Password");
        }
        if (masterPassword == null || masterPassword.length() < VaultConstants.MIN_MASTER_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("The Master Password must be at least "
                    + VaultConstants.MIN_MASTER_PASSWORD_LENGTH + " characters long");
        }
        if (!masterPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("The two Master Passwords do not match");
        }

        byte[] salt = VaultCrypto.randomBytes(VaultConstants.SALT_BYTES);
        SecretKey kek = VaultCrypto.deriveKek(masterPassword, salt);
        SecretKey dek = VaultCrypto.randomDek();

        // Only the wrapped DEK and the salt reach the database.
        VaultCrypto.Sealed wrapped = VaultCrypto.encrypt(kek, dek.getEncoded());

        VaultConfig config = new VaultConfig();
        config.setId(VaultConstants.CONFIG_ID);
        config.setSalt(VaultCrypto.base64(salt));
        config.setEncryptedDek(VaultCrypto.base64(wrapped.cipherText()));
        config.setDekIv(VaultCrypto.base64(wrapped.iv()));
        config.setCreatedAt(LocalDateTime.now(clock));
        configRepository.save(config);

        session.unlock(dek);
    }

    @Override
    public void unlock(String masterPassword) {
        session.unlock(dekOf(requireConfig(), masterPassword));
    }

    @Override
    public void lock() {
        session.lock();
    }

    @Override
    public List<CredentialView> list() {
        List<CredentialView> views = new ArrayList<>();
        for (Credential credential : credentialRepository.findByOwnerPanOrderByAppNameAsc(ownerPan())) {
            views.add(viewOf(credential));
        }
        return views;
    }

    @Override
    @Transactional
    public CredentialView create(CredentialRequest request) {
        SecretKey dek = session.requireDek();
        Credential credential = new Credential();
        credential.setOwnerPan(ownerPan());
        apply(credential, request);
        if (request.password() == null || request.password().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        encryptPassword(credential, request.password(), dek);

        LocalDateTime now = LocalDateTime.now(clock);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);

        Credential saved = credentialRepository.save(credential);
        audit(VaultConstants.ACTION_CREATE, saved.getId());
        return viewOf(saved);
    }

    @Override
    @Transactional
    public CredentialView update(Long id, CredentialRequest request) {
        Credential credential = require(id);
        SecretKey dek = session.requireDek();
        apply(credential, request);
        // An empty password keeps the stored ciphertext: the edit screen never
        // reveals a password, so it cannot resend it either.
        if (request.password() != null && !request.password().isEmpty()) {
            encryptPassword(credential, request.password(), dek);
        }
        credential.setUpdatedAt(LocalDateTime.now(clock));

        Credential saved = credentialRepository.save(credential);
        audit(VaultConstants.ACTION_UPDATE, id);
        return viewOf(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Credential credential = require(id);
        credentialRepository.delete(credential);
        audit(VaultConstants.ACTION_DELETE, id);
    }

    @Override
    public String reveal(Long id, String masterPassword) {
        return decryptPassword(id, masterPassword, VaultConstants.ACTION_VIEW_PASSWORD);
    }

    @Override
    public String copy(Long id, String masterPassword) {
        return decryptPassword(id, masterPassword, VaultConstants.ACTION_COPY_PASSWORD);
    }

    // ------------------------------------------------------------------ internals

    /**
     * CRED_V1 Reveal/Copy: verify the Master Password again for THIS request,
     * unwrap the DEK with it, decrypt one password. The DEK obtained here is not
     * kept, so revealing never extends the session.
     */
    private String decryptPassword(Long id, String masterPassword, String action) {
        Credential credential = require(id);
        SecretKey dek = dekOf(requireConfig(), masterPassword);
        try {
            byte[] plain = VaultCrypto.decrypt(dek,
                    VaultCrypto.unbase64(credential.getPasswordIv()),
                    VaultCrypto.unbase64(credential.getEncryptedPassword()));
            audit(action, id);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (VaultCrypto.VaultCryptoException e) {
            // Tampered row or a DEK that does not belong to it - never guess.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This credential cannot be decrypted with the current key");
        }
    }

    /** KEK from the stored salt -> unwrap the DEK. A wrong password fails the GCM tag. */
    private SecretKey dekOf(VaultConfig config, String masterPassword) {
        if (masterPassword == null || masterPassword.isEmpty()) {
            throw new IllegalArgumentException("Master Password is required");
        }
        SecretKey kek = VaultCrypto.deriveKek(masterPassword, VaultCrypto.unbase64(config.getSalt()));
        try {
            byte[] dek = VaultCrypto.decrypt(kek,
                    VaultCrypto.unbase64(config.getDekIv()),
                    VaultCrypto.unbase64(config.getEncryptedDek()));
            return VaultCrypto.keyFromBase64(VaultCrypto.base64(dek));
        } catch (VaultCrypto.VaultCryptoException e) {
            throw new IllegalArgumentException(WRONG_MASTER);
        }
    }

    private void encryptPassword(Credential credential, String password, SecretKey dek) {
        VaultCrypto.Sealed sealed = VaultCrypto.encrypt(dek, password.getBytes(StandardCharsets.UTF_8));
        credential.setEncryptedPassword(VaultCrypto.base64(sealed.cipherText()));
        credential.setPasswordIv(VaultCrypto.base64(sealed.iv()));
    }

    /** The fields CRED_V1 lists for both screens, trimmed and bounded. */
    private void apply(Credential credential, CredentialRequest request) {
        // Client PAN is the signed-in client's own PAN, so it comes from the
        // session - the read-only screen field never decides it.
        credential.setClientPan(ownerPan());
        credential.setAppName(text(request.appName(), "Application Name", 120));
        credential.setUsername(text(request.username(), "Username", 160));
        credential.setWebsite(optionalText(request.website(), 300));
        credential.setNotes(optionalText(request.notes(), 2000));
    }

    private String text(String value, String label, int maxLength) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(label + " is longer than " + maxLength + " characters");
        }
        return text;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    /** One credential of the signed-in client, or 404 - never another client's row. */
    private Credential require(Long id) {
        List<Credential> rows = credentialRepository.findByIdAndOwnerPan(id, ownerPan());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found");
        }
        return rows.get(0);
    }

    private String ownerPan() {
        return currentUserService.requireClient().getPan();
    }

    private VaultConfig config() {
        List<VaultConfig> rows = configRepository.findAll();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private VaultConfig requireConfig() {
        VaultConfig config = config();
        if (config == null) {
            throw new IllegalArgumentException("No vault exists yet - create one first");
        }
        return config;
    }

    private CredentialView viewOf(Credential credential) {
        return new CredentialView(credential.getId(), credential.getClientPan(),
                credential.getAppName(), credential.getUsername(), credential.getWebsite(),
                credential.getNotes(), credential.getCreatedAt(), credential.getUpdatedAt());
    }

    /** The audit row carries who and when - never what. */
    private void audit(String action, Long credentialId) {
        CredentialAudit entry = new CredentialAudit();
        entry.setActionType(action);
        entry.setCredentialId(credentialId);
        entry.setPerformedAt(LocalDateTime.now(clock));
        entry.setPerformedBy(currentUserService.currentPan());
        auditRepository.save(entry);
    }
}
