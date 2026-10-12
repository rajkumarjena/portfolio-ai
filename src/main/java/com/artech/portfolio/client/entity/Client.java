package com.artech.portfolio.client.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
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
