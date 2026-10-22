package com.artech.portfolio.auth.security;

import com.artech.portfolio.auth.util.AuthConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.client.util.ClientConstants;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AUTH.md (a) + (b): a user IS a client, identified by PAN number.
 * The login name is the PAN; the password is stored as a one-way BCrypt hash.
 */
@Service
public class PortfolioUserDetailsService implements UserDetailsService {

    private final ClientRepository clientRepository;

    public PortfolioUserDetailsService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String pan) throws UsernameNotFoundException {
        if (pan == null || pan.isBlank()) {
            throw new UsernameNotFoundException("PAN is required");
        }
        List<Client> clients = clientRepository.findByPan(pan.trim().toUpperCase());
        if (clients.isEmpty()) {
            // Generic message: never reveal whether the PAN exists (anti-enumeration)
            throw new UsernameNotFoundException("Invalid PAN or password");
        }
        Client client = clients.get(0);
        if (client.getPasswordHash() == null || client.getPasswordHash().isBlank()) {
            // Client exists but never registered a login (created via client config only)
            throw new UsernameNotFoundException("Invalid PAN or password");
        }

        String role = client.getRole() == null || client.getRole().isBlank()
                ? ClientConstants.ROLE_USER : client.getRole();

        boolean accountLocked = client.getLockedUntil() != null
                && client.getLockedUntil() > System.currentTimeMillis();

        // BONDBP_V10 #4: HOLD/CANCELLED accounts cannot log in
        String status = client.getStatus() == null ? ClientConstants.STATUS_ACTIVE : client.getStatus();
        if (ClientConstants.STATUS_HOLD.equals(status)) {
            throw new org.springframework.security.authentication.DisabledException("Account on hold");
        }
        if (ClientConstants.STATUS_CANCELLED.equals(status)) {
            throw new org.springframework.security.authentication.DisabledException("Account cancelled");
        }

        return User.builder()
                .username(client.getPan())
                .password(client.getPasswordHash())
                .authorities(AuthConstants.ROLE_PREFIX + role)
                .accountLocked(accountLocked)
                .build();
    }
}
