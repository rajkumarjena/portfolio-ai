package com.artech.portfolio.auth.service.impl;

import com.artech.portfolio.auth.dto.ForgotPasswordRequest;
import com.artech.portfolio.auth.dto.RegisterRequest;
import com.artech.portfolio.auth.dto.ResetPasswordRequest;
import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.auth.service.AuthService;
import com.artech.portfolio.auth.service.EmailService;
import com.artech.portfolio.auth.util.AuthConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientEmail;
import com.artech.portfolio.client.entity.ClientMobile;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.client.util.ClientConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String appBaseUrl;

    public AuthServiceImpl(ClientRepository clientRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    @Transactional
    public Client register(RegisterRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        String pan = request.getPan() == null ? "" : request.getPan().trim().toUpperCase();
        String password = request.getPassword() == null ? "" : request.getPassword();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        if (name.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (!pan.matches(ClientConstants.PAN_REGEX)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "PAN must match the format ABCDE1234F (5 letters, 4 digits, 1 letter)");
        }
        if (!password.matches(AuthConstants.PASSWORD_REGEX)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, AuthConstants.PASSWORD_POLICY_MESSAGE);
        }
        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "A valid email address is required (used for password reset)");
        }

        // AUTH.md (b): a user IS a client. If the PAN already exists as a client
        // (created via client-config without a login), adopt that record instead of
        // creating a duplicate; if it already has a password, registration is closed.
        List<Client> existing = clientRepository.findByPan(pan);
        if (!existing.isEmpty()) {
            Client client = existing.get(0);
            if (client.getPasswordHash() != null && !client.getPasswordHash().isBlank()) {
                throw new AuthException(HttpStatus.CONFLICT, "An account with this PAN already exists");
            }
            client.setPasswordHash(passwordEncoder.encode(password));
            client.setRole(client.getRole() == null || client.getRole().isBlank()
                    ? (hasAdmin() ? ClientConstants.ROLE_USER : ClientConstants.ROLE_ADMIN)
                    : client.getRole());
            if (client.getFailedAttempts() != 0 || client.getLockedUntil() != null) {
                client.setFailedAttempts(0);
                client.setLockedUntil(null);
            }
            if (!emailMatches(client, email)) {
                ClientEmail clientEmail = new ClientEmail();
                clientEmail.setEmail(email);
                client.addEmail(clientEmail);
            }
            String mobile = request.getMobileNumber() == null ? "" : request.getMobileNumber().trim();
            if (!mobile.isEmpty() && mobile.matches("[0-9]{10}")
                    && client.getMobileNumbers().stream()
                            .noneMatch(m -> mobile.equals(m.getMobileNumber()))) {
                ClientMobile clientMobile = new ClientMobile();
                clientMobile.setMobileNumber(mobile);
                client.addMobile(clientMobile);
            }
            return clientRepository.save(client);
        }

        Client client = new Client();
        client.setName(name);
        client.setPan(pan);
        client.setPasswordHash(passwordEncoder.encode(password));
        // AUTH.md (d): default role is USER. If no ADMIN exists yet, the first account
        // bootstraps as ADMIN, otherwise the role-elevation process from AUTH.md (e)
        // could never be started (and (d) still makes every later sign-up a USER).
        client.setRole(hasAdmin() ? ClientConstants.ROLE_USER : ClientConstants.ROLE_ADMIN);

        ClientEmail clientEmail = new ClientEmail();
        clientEmail.setEmail(email);
        client.addEmail(clientEmail);

        String mobile = request.getMobileNumber() == null ? "" : request.getMobileNumber().trim();
        if (!mobile.isEmpty()) {
            if (!mobile.matches("[0-9]{10}")) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "Mobile number must be 10 digits");
            }
            ClientMobile clientMobile = new ClientMobile();
            clientMobile.setMobileNumber(mobile);
            client.addMobile(clientMobile);
        }

        return clientRepository.save(client);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String pan = request.getPan() == null ? "" : request.getPan().trim().toUpperCase();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        List<Client> clients = pan.isEmpty() ? List.of() : clientRepository.findByPan(pan);
        Client client = clients.isEmpty() ? null : clients.get(0);

        // Same generic response whether the PAN/email matches or not: no account enumeration
        if (client == null || !emailMatches(client, email)) {
            return;
        }
        if (client.getEmails().isEmpty()) {
            return;
        }

        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        client.setResetTokenHash(sha256(token));
        client.setResetTokenExpiry(System.currentTimeMillis() + AuthConstants.RESET_TOKEN_TTL_MS);
        clientRepository.save(client);

        String resetLink = appBaseUrl + "/reset-password.html?token=" + token;
        emailService.sendPasswordResetEmail(email, client.getName(), resetLink);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String token = request.getToken() == null ? "" : request.getToken().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (token.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
        }
        if (!password.matches(AuthConstants.PASSWORD_REGEX)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, AuthConstants.PASSWORD_POLICY_MESSAGE);
        }

        List<Client> clients = clientRepository.findByResetTokenHash(sha256(token));
        if (clients.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
        }
        Client client = clients.get(0);

        if (client.getResetTokenExpiry() == null
                || client.getResetTokenExpiry() < System.currentTimeMillis()) {
            client.setResetTokenHash(null);
            client.setResetTokenExpiry(null);
            clientRepository.save(client);
            throw new AuthException(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
        }

        client.setPasswordHash(passwordEncoder.encode(password));
        // Single-use token + clear any brute-force lock now that ownership is proven
        client.setResetTokenHash(null);
        client.setResetTokenExpiry(null);
        client.setFailedAttempts(0);
        client.setLockedUntil(null);
        clientRepository.save(client);
    }

    @Override
    @Transactional
    public boolean isResetTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        List<Client> clients = clientRepository.findByResetTokenHash(sha256(token.trim()));
        if (clients.isEmpty()) {
            return false;
        }
        Client client = clients.get(0);
        return client.getResetTokenExpiry() != null
                && client.getResetTokenExpiry() >= System.currentTimeMillis();
    }

    private boolean emailMatches(Client client, String email) {
        if (email.isEmpty()) {
            return false;
        }
        for (ClientEmail e : client.getEmails()) {
            if (email.equalsIgnoreCase(e.getEmail())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdmin() {
        return !clientRepository.findByRole(ClientConstants.ROLE_ADMIN).isEmpty();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
