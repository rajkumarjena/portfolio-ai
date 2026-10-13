package com.artech.portfolio.auth.security;

import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * On a successful login: clear the brute-force counters and honour the ?next=
 * deep link captured by the entry point (so "first page of the application"
 * still lands the user where they originally asked to go, defaulting to index).
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ClientRepository clientRepository;

    public LoginSuccessHandler(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String pan = authentication.getName();
        List<Client> clients = clientRepository.findByPan(pan);
        if (!clients.isEmpty()) {
            Client client = clients.get(0);
            if (client.getFailedAttempts() != 0 || client.getLockedUntil() != null) {
                client.setFailedAttempts(0);
                client.setLockedUntil(null);
                clientRepository.save(client);
            }
        }

        String next = request.getParameter("next");
        String target = "/index.html";
        if (next != null && !next.isBlank()) {
            String decoded = URLDecoder.decode(next, StandardCharsets.UTF_8);
            // Only allow same-site relative redirects (no open redirects)
            if (decoded.startsWith("/") && !decoded.startsWith("//")) {
                target = decoded;
            }
        }
        response.sendRedirect(request.getContextPath() + target);
    }
}
