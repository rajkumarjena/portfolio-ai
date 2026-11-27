package com.artech.portfolio.credential;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.credential.dto.CredentialRequest;
import com.artech.portfolio.credential.dto.CredentialView;
import com.artech.portfolio.credential.dto.VaultStatusView;
import com.artech.portfolio.credential.entity.Credential;
import com.artech.portfolio.credential.entity.CredentialAudit;
import com.artech.portfolio.credential.repository.CredentialAuditRepository;
import com.artech.portfolio.credential.repository.CredentialRepository;
import com.artech.portfolio.credential.service.CredentialVaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * CRED_V1 end to end on a throwaway SQLite database (never the real
 * portfolio.db): create the vault, add a credential, reveal and copy it, edit it,
 * lock and unlock the session, and delete it - plus the ownership and audit
 * rules around all of that.
 */
@SpringBootTest
class CredentialVaultFlowTest {

    private static final String MASTER = "MyPortfolio@2026";
    private static final String PASSWORD = "MySecretPassword123";
    private static final String OWNER_PAN = "ABCDE1234F";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        Path file = Files.createTempFile("credential-vault-", ".db");
        Files.deleteIfExists(file);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + file.toAbsolutePath());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private CredentialVaultService vaultService;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private CredentialAuditRepository auditRepository;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Client owner(String pan) {
        Client client = new Client();
        client.setName("Test User");
        client.setPan(pan);
        return client;
    }

    @Test
    void theWholeVaultFlowWorks() {
        when(currentUserService.requireClient()).thenReturn(owner(OWNER_PAN));
        when(currentUserService.currentPan()).thenReturn(OWNER_PAN);

        // ---------------------------------------------------------- create vault
        VaultStatusView before = vaultService.status();
        assertFalse(before.initialized(), "a fresh database has no vault");
        assertFalse(before.unlocked(), "nothing is in memory yet");

        vaultService.setup(MASTER, MASTER);

        VaultStatusView afterSetup = vaultService.status();
        assertTrue(afterSetup.initialized(), "vault_config now exists");
        assertTrue(afterSetup.unlocked(), "setup leaves the vault unlocked");

        // -------------------------------------------------------- add credential
        CredentialView created = vaultService.create(new CredentialRequest(
                OWNER_PAN, "Gmail", "raj@gmail.com", PASSWORD, "gmail.com", "personal mail"));
        assertNotNull(created.id());
        assertEquals("Gmail", created.appName());

        // The database must only ever hold the ciphertext.
        Credential stored = credentialRepository.findById(created.id()).orElseThrow();
        assertNotEquals(PASSWORD, stored.getEncryptedPassword());
        assertFalse(stored.getEncryptedPassword().contains(PASSWORD));
        assertNotNull(stored.getPasswordIv());

        // The listing never carries a password at all.
        List<CredentialView> listed = vaultService.list();
        assertEquals(1, listed.size());
        assertEquals(OWNER_PAN, listed.get(0).clientPan());

        // ------------------------------------------------------- reveal and copy
        assertThrows(IllegalArgumentException.class,
                () -> vaultService.reveal(created.id(), "WrongPassword@1"),
                "a wrong Master Password is rejected");
        assertEquals(PASSWORD, vaultService.reveal(created.id(), MASTER));
        assertEquals(PASSWORD, vaultService.copy(created.id(), MASTER));

        // --------------------------------------------------------------- edit
        vaultService.update(created.id(), new CredentialRequest(
                OWNER_PAN, "Gmail Work", "raj@gmail.com", "", "mail.google.com", "work"));
        Credential edited = credentialRepository.findById(created.id()).orElseThrow();
        assertEquals("Gmail Work", edited.getAppName());
        assertEquals(PASSWORD, vaultService.reveal(created.id(), MASTER),
                "a blank password keeps the stored ciphertext");

        // Empty password on a NEW credential is refused.
        assertThrows(IllegalArgumentException.class, () -> vaultService.create(
                new CredentialRequest(OWNER_PAN, "Zerodha", "raj123", "", null, null)));

        // The Client PAN is never taken from the request body: the screen shows
        // the signed-in client's own PAN read-only, so that is what gets stored.
        CredentialView zerodha = vaultService.create(
                new CredentialRequest("not-a-pan", "Zerodha", "raj123", PASSWORD, null, null));
        assertEquals(OWNER_PAN, zerodha.clientPan(), "the signed-in client's PAN wins");

        // ------------------------------------------------------------- lock
        vaultService.lock();
        assertFalse(vaultService.status().unlocked());
        assertThrows(IllegalStateException.class, () -> vaultService.create(
                new CredentialRequest(OWNER_PAN, "Zerodha", "raj123", PASSWORD, null, null)),
                "a locked vault cannot encrypt a new credential");

        vaultService.unlock(MASTER);
        assertTrue(vaultService.status().unlocked());

        // ------------------------------------------------ ownership isolation
        when(currentUserService.requireClient()).thenReturn(owner("ZZZZZ9999Z"));
        when(currentUserService.currentPan()).thenReturn("ZZZZZ9999Z");
        assertTrue(vaultService.list().isEmpty(), "one client never sees another's rows");
        assertThrows(ResponseStatusException.class,
                () -> vaultService.reveal(created.id(), MASTER));

        // ------------------------------------------------------------- delete
        when(currentUserService.requireClient()).thenReturn(owner(OWNER_PAN));
        when(currentUserService.currentPan()).thenReturn(OWNER_PAN);
        vaultService.delete(created.id());
        vaultService.delete(zerodha.id());
        assertTrue(vaultService.list().isEmpty());

        // -------------------------------------------------------------- audit
        List<CredentialAudit> audit = auditRepository.findAll();
        List<String> actions = audit.stream().map(CredentialAudit::getActionType).toList();
        assertTrue(actions.containsAll(List.of("CREATE", "UPDATE",
                "VIEW_PASSWORD", "COPY_PASSWORD", "DELETE")), "every action is audited: " + actions);
        assertTrue(audit.stream().allMatch(a -> OWNER_PAN.equals(a.getPerformedBy())
                        || "ZZZZZ9999Z".equals(a.getPerformedBy())),
                "the audit names the user, never the password");
        assertTrue(audit.stream().noneMatch(a -> a.getActionType() == null));
    }
}
