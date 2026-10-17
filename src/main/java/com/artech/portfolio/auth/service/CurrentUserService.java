package com.artech.portfolio.auth.service;

import com.artech.portfolio.auth.dto.CurrentUserView;
import com.artech.portfolio.auth.util.AuthConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves the signed-in client from the security context.
 *
 * AUTH.md b: a user IS a client identified by PAN, so every investment record
 * (FD, bond, mutual fund - CONSISTENCY.md 6) is owned by exactly this client.
 * Services use it to bind ownership on save and to scope every query, instead
 * of trusting an owner id coming in a request body.
 */
@Service
public class CurrentUserService {

    private final ClientRepository clientRepository;

    public CurrentUserService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /** PAN of the signed-in user, or null when anonymous. */
    public String currentPan() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    /** The signed-in client row, or null if anonymous / PAN no longer exists. */
    public Client currentClient() {
        String pan = currentPan();
        if (pan == null) {
            return null;
        }
        List<Client> clients = clientRepository.findByPan(pan);
        return clients.isEmpty() ? null : clients.get(0);
    }

    /**
     * The signed-in client, required - throws when there is no session.
     * Used on every investment save so client_id is never null (CONSISTENCY.md 6).
     */
    public Client requireClient() {
        Client client = currentClient();
        if (client == null) {
            throw new IllegalStateException("Authentication required");
        }
        return client;
    }

    /**
     * Page-header snapshot for the templates (CONSISTENCY.md 5), or null when
     * anonymous. Rendered server-side - pages never fetch it themselves.
     */
    public CurrentUserView view() {
        Client client = currentClient();
        if (client == null) {
            return null;
        }
        return new CurrentUserView(client.getName(), client.getPan(),
                client.getRole(), "ADMIN".equals(client.getRole()));
    }

    /** True when the signed-in user holds any of the given roles (AUTH.md d). */
    public boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (String role : roles) {
            boolean matched = auth.getAuthorities().stream()
                    .anyMatch(a -> (AuthConstants.ROLE_PREFIX + role).equals(a.getAuthority()));
            if (matched) {
                return true;
            }
        }
        return false;
    }

    /** ADMIN sees every client's investments; USER / SUPERVISOR see only their own. */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> (AuthConstants.ROLE_PREFIX + "ADMIN").equals(a.getAuthority()));
    }
}
