package com.artech.portfolio.auth.controller;

import com.artech.portfolio.auth.dto.AdminUserDto;
import com.artech.portfolio.auth.dto.RoleUpdateRequest;
import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.auth.util.AuthConstants;
import com.artech.portfolio.client.entity.Client;
import com.artech.portfolio.client.entity.ClientEmail;
import com.artech.portfolio.client.repository.ClientRepository;
import com.artech.portfolio.client.util.ClientConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AUTH.md (e): the admin business process - list users and elevate any user
 * to SUPERVISOR or ADMIN (and demote back to USER). ADMIN role only.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final ClientRepository clientRepository;

    public AdminUserController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<AdminUserDto> listUsers() {
        return clientRepository.findAll().stream().map(this::toDto).toList();
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<AdminUserDto> updateRole(@PathVariable Long id,
                                                   @RequestBody RoleUpdateRequest request,
                                                   Authentication authentication) {
        String requestedRole = request.getRole() == null ? "" : request.getRole().trim().toUpperCase();
        if (!Arrays.asList(ClientConstants.ALL_ROLES).contains(requestedRole)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Role must be one of USER, SUPERVISOR, ADMIN");
        }

        // An admin must not change their own role - that could lock everyone out of ADMIN
        if (authentication != null
                && authentication.getName() != null
                && clientRepository.findById(id)
                    .filter(c -> authentication.getName().equalsIgnoreCase(c.getPan()))
                    .isPresent()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "You cannot change your own role; ask another admin to do it");
        }

        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found with id " + id));

        client.setRole(requestedRole);
        Client saved = clientRepository.save(client);
        return ResponseEntity.ok(toDto(saved));
    }

    private AdminUserDto toDto(Client client) {
        AdminUserDto dto = new AdminUserDto();
        dto.setId(client.getId());
        dto.setName(client.getName());
        dto.setPan(client.getPan());
        dto.setRole(client.getRole() == null ? ClientConstants.ROLE_USER : client.getRole());
        dto.setEmails(client.getEmails().stream().map(ClientEmail::getEmail).toList());
        dto.setLocked(client.getLockedUntil() != null
                && client.getLockedUntil() > System.currentTimeMillis());
        dto.setFailedAttempts(client.getFailedAttempts());
        return dto;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}
