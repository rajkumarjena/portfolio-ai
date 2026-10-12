package com.artech.portfolio.client.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

@Entity
@Data
public class ClientBankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bankName;
    private String accountNumber;
    private String ifsc;
    private String bankAddress;

    @ManyToOne
    @JoinColumn(
            name = "client_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @JsonBackReference
    @ToString.Exclude
    private Client client;
}
