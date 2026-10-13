package com.artech.portfolio.auth.controller;

import com.artech.portfolio.auth.dto.ForgotPasswordRequest;
import com.artech.portfolio.auth.dto.RegisterRequest;
import com.artech.portfolio.auth.dto.ResetPasswordRequest;
import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.auth.service.AuthService;
import com.artech.portfolio.auth.util.AuthConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.repository.ClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public authentication endpoints: register, forgot/reset password,
 * plus /api/auth/me so the front-ends can show who is logged in.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientRepository clientRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    public AuthController(AuthService authService, ClientRepository clientRepository,
                          CookieCsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.clientRepository = clientRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * Issues the XSRF-TOKEN cookie on demand. The static login pages load this first
     * so subsequent form posts / fetches can echo the token back in X-XSRF-TOKEN.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        // After a successful login Spring Security invalidates the token and expires the
        // cookie; if a stale/empty cookie survives in the browser, force a fresh one.
        if (token == null || token.getToken() == null || token.getToken().isBlank()) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return ResponseEntity.ok(Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName()));
    }

    /** Lets the reset-password page reject dead links before asking for a new password. */
    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, String>> validateToken(@RequestParam String token) {
        boolean valid = authService.isResetTokenValid(token);
        if (valid) {
            return ResponseEntity.ok(Map.of("message", "Token is valid"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "Reset link is invalid or has expired"));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Client client = authService.register(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Registration successful. Please sign in.");
        body.put("pan", client.getPan());
        body.put("role", client.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // Generic message on purpose - never reveal whether PAN/email matched
        return ResponseEntity.ok(Map.of("message",
                "If that PAN and email match an account, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated. Please sign in."));
    }

    /** Who am I - used by the pages to gate admin-only UI. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pan", authentication.getName());
        String authority = authentication.getAuthorities().iterator().next().getAuthority();
        String role = authority.startsWith(AuthConstants.ROLE_PREFIX)
                ? authority.substring(AuthConstants.ROLE_PREFIX.length()) : authority;
        body.put("role", role);
        // Friendly display name for the dashboard header
        List<Client> clients = clientRepository.findByPan(authentication.getName());
        body.put("name", clients.isEmpty() ? authentication.getName() : clients.get(0).getName());
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}
