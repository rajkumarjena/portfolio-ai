package com.artech.portfolio.client.entity;

import com.artech.portfolio.client.util.ClientConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // PAN is the unique business identifier: a client is identified by PAN
    private String pan;

    // ------------------------------------------------------------------
    // Authentication (AUTH.md b: a user IS a client, identified by PAN)
    // ------------------------------------------------------------------

    // One-way BCrypt hash - never serialized to JSON and never accepted from a request body
    @JsonIgnore
    private String passwordHash;

    // USER | SUPERVISOR | ADMIN - readable by the UI but read-only over JSON so a
    // user can never self-assign a higher role through the client API
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String role = ClientConstants.ROLE_USER;

    // Brute-force protection (AUTH.md g): consecutive failed logins and lock expiry (epoch millis)
    // columnDefinition matters: SQLite refuses ALTER TABLE ADD COLUMN of a NOT NULL
    // column without a default, so the DDL must carry "default 0" explicitly.
    @JsonIgnore
    @Column(name = "failed_attempts", columnDefinition = "integer not null default 0")
    private int failedAttempts;

    @JsonIgnore
    private Long lockedUntil;

    // Password reset (AUTH.md h): SHA-256 of the emailed token + expiry (epoch millis)
    @JsonIgnore
    private String resetTokenHash;

    @JsonIgnore
    private Long resetTokenExpiry;

    // A client can have more than one mobile number, email address and bank account
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ClientMobile> mobileNumbers = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ClientEmail> emails = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ClientBankAccount> bankAccounts = new ArrayList<>();

    /** Helper utilities to safely attach children while maintaining the bidirectional link */
    public void addMobile(ClientMobile mobile) {
        mobileNumbers.add(mobile);
        mobile.setClient(this);
    }

    public void addEmail(ClientEmail email) {
        emails.add(email);
        email.setClient(this);
    }

    public void addBankAccount(ClientBankAccount bankAccount) {
        bankAccounts.add(bankAccount);
        bankAccount.setClient(this);
    }
}
