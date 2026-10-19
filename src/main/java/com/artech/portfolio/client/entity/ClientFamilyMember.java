package com.artech.portfolio.client.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

/**
 * BONDBP_V5 #4: a family-member association links a client (the owner who
 * clicked "Add") to ANOTHER already registered client - the member is picked
 * from the client table through the search drop-down, never typed in by hand.
 *
 * Only the link itself is stored here; the member's name, PAN, emails etc.
 * always come from that member's own Client row (BONDBP_V5 #4.c: "details
 * ... from client table").
 */
@Entity
@Data
public class ClientFamilyMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The client who owns the association (the one working the Client View panel)
    @ManyToOne
    @JoinColumn(
            name = "client_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @JsonBackReference
    @ToString.Exclude
    private Client client;

    // The associated registered client (the family member).
    // Serialised as a shallow view {id, name, pan, emails, mobileNumbers} only,
    // so JSON can never recurse into that client's own family list.
    // Column stays NULL-tolerant because SQLite cannot ALTER an existing table
    // to NOT NULL - ClientServiceImpl.addFamilyMember always sets it.
    @ManyToOne
    @JoinColumn(
            name = "member_client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @JsonIncludeProperties({"id", "name", "pan", "emails", "mobileNumbers"})
    @ToString.Exclude
    private Client member;
}
