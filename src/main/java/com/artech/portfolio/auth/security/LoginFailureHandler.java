package com.artech.portfolio.auth.security;

import com.artech.portfolio.auth.util.AuthConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * AUTH.md (g): best secured login - consecutive failed attempts lock the account
 * for a cool-down period, so credential stuffing / brute force stops being useful.
 * Also counts attempts against unknown PANs only when the client exists, so the
 * error message for unknown PANs stays identical (no enumeration).
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final ClientRepository clientRepository;

    public LoginFailureHandler(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String pan = request.getParameter("username");
        boolean locked = false;
        if (pan != null && !pan.isBlank()) {
            List<Client> clients = clientRepository.findByPan(pan.trim().toUpperCase());
            if (!clients.isEmpty()) {
                Client client = clients.get(0);
                long now = System.currentTimeMillis();
                boolean stillLocked = client.getLockedUntil() != null && client.getLockedUntil() > now;
                if (stillLocked) {
                    // Already locked: keep counting stopped so the window never extends
                    locked = true;
                } else {
                    // First failure after (or without) a previous lock: restart the window
                    if (client.getLockedUntil() != null) {
                        client.setFailedAttempts(0);
                        client.setLockedUntil(null);
                    }
                    int attempts = client.getFailedAttempts() + 1;
                    client.setFailedAttempts(attempts);
                    if (attempts >= AuthConstants.MAX_FAILED_ATTEMPTS) {
                        client.setLockedUntil(now + AuthConstants.LOCK_DURATION_MS);
                        locked = true;
                    }
                }
                clientRepository.save(client);
            }
        }

        String next = request.getParameter("next");
        StringBuilder redirect = new StringBuilder(request.getContextPath());
        // BONDBP_V10 #4: show specific messages for HOLD/CANCELLED
        String exMsg = exception == null ? "" : exception.getMessage();
        if (exMsg != null && exMsg.contains("on hold")) {
            redirect.append("/login.html?status=HOLD");
        } else if (exMsg != null && exMsg.contains("cancelled")) {
            redirect.append("/login.html?status=CANCELLED");
        } else {
            redirect.append("/login.html?error=1");
            if (locked) {
                redirect.append("&locked=1");
            }
        }
        if (next != null && !next.isBlank()) {
            redirect.append("&next=").append(java.net.URLEncoder.encode(next, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (response.isCommitted()) {
            return;
        }
        response.sendRedirect(redirect.toString());
    }
}
