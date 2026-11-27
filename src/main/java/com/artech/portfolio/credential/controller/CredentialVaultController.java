package com.artech.portfolio.credential.controller;

import com.artech.portfolio.credential.dto.CredentialRequest;
import com.artech.portfolio.credential.dto.CredentialView;
import com.artech.portfolio.credential.dto.RevealedPasswordView;
import com.artech.portfolio.credential.dto.VaultPasswordRequest;
import com.artech.portfolio.credential.dto.VaultSetupRequest;
import com.artech.portfolio.credential.dto.VaultStatusView;
import com.artech.portfolio.credential.service.CredentialVaultService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * CRED_V1 - the Credential Vault API.
 *
 * <p>Three answers mean three different things to the screen: 400 is a wrong
 * password or a missing field, 404 is a credential that is not the signed-in
 * client's, and 423 means the vault is locked and has to be unlocked again.</p>
 */
@RestController
@RequestMapping("/api/vault")
public class CredentialVaultController {

    private final CredentialVaultService vaultService;

    public CredentialVaultController(CredentialVaultService vaultService) {
        this.vaultService = vaultService;
    }

    /** Initialised / unlocked - drives which panel the page shows. */
    @GetMapping("/status")
    public VaultStatusView status() {
        return vaultService.status();
    }

    /** CRED_V1 "Create Vault". */
    @PostMapping("/setup")
    public VaultStatusView setup(@RequestBody VaultSetupRequest request) {
        vaultService.setup(request == null ? null : request.masterPassword(),
                request == null ? null : request.confirmPassword());
        return vaultService.status();
    }

    /** CRED_V1 "Unlock Vault". */
    @PostMapping("/unlock")
    public VaultStatusView unlock(@RequestBody VaultPasswordRequest request) {
        vaultService.unlock(request == null ? null : request.masterPassword());
        return vaultService.status();
    }

    /** Drop the DEK from memory now (the 10 minute idle lock does the same). */
    @PostMapping("/lock")
    public VaultStatusView lock() {
        vaultService.lock();
        return vaultService.status();
    }

    /** The listing - masked, never a password. */
    @GetMapping("/credentials")
    public List<CredentialView> list() {
        return vaultService.list();
    }

    @PostMapping("/credentials")
    public CredentialView create(@RequestBody CredentialRequest request) {
        return vaultService.create(request);
    }

    /** An empty password keeps the stored one (the edit screen never reveals it). */
    @PutMapping("/credentials/{id}")
    public CredentialView update(@PathVariable("id") Long id,
                                 @RequestBody CredentialRequest request) {
        return vaultService.update(id, request);
    }

    /** Returns the refreshed listing so the table redraws from the server. */
    @DeleteMapping("/credentials/{id}")
    public List<CredentialView> delete(@PathVariable("id") Long id) {
        vaultService.delete(id);
        return vaultService.list();
    }

    /** CRED_V1 Reveal: verify the Master Password for this one request. */
    @PostMapping("/credentials/{id}/reveal")
    public RevealedPasswordView reveal(@PathVariable("id") Long id,
                                       @RequestBody VaultPasswordRequest request) {
        return new RevealedPasswordView(
                vaultService.reveal(id, request == null ? null : request.masterPassword()));
    }

    /** CRED_V1 Copy: same verification, audited separately. */
    @PostMapping("/credentials/{id}/copy")
    public RevealedPasswordView copy(@PathVariable("id") Long id,
                                     @RequestBody VaultPasswordRequest request) {
        return new RevealedPasswordView(
                vaultService.copy(id, request == null ? null : request.masterPassword()));
    }

    // ------------------------------------------------------------------ errors

    /** 423: the vault locked itself while the page was open. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleLocked(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(Map.of("message", ex.getMessage() == null ? "The vault is locked" : ex.getMessage()));
    }

    /** 400: a wrong Master Password or a missing / over-long field. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() == null ? "Request failed" : ex.getMessage()));
    }

    /** The service's 404 / 409 keep their own status. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }
}
