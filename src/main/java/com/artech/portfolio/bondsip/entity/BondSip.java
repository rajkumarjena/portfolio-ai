package com.artech.portfolio.bondsip.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V7 - Bond SIP (#1): Issuer, Create Date, SIP Date, SIP Amount,
 * Actual SIP Amount, Bond Name, Status.
 *
 * "Bond Name" is not stored here: it is derived on the list screen from the
 * Bond SIP Transaction created when the SIP executed (#4) and associated with
 * a bond (#6/#7). "Actual SIP Amount" is mirrored from that transaction once
 * the user enters it on the Bond SIP Transaction screen (#5).
 */
@Entity
@Data
public class BondSip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CONSISTENCY.md 6: the SIP belongs to the signed-in client - bound from
    // the session on save, never from the request body.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** #2: mandatory. */
    private String issuer;

    /** #2: auto-populated to the current date server-side; the form field is disabled. */
    private LocalDate createDate;

    /** #2: mandatory - the calendar date the SIP is scheduled for. */
    private LocalDate sipDate;

    /** #2: mandatory planned amount. */
    private double sipAmount;

    /**
     * Frequency of the instalments (like a bond's coupon frequency):
     * WEEKLY / MONTHLY / QUARTERLY / HALF-YEARLY / YEARLY. Every due instalment
     * from the SIP Date onwards auto-creates one Bond SIP Transaction
     * (INPROGRESS); a SIP entered with a past start date is backfilled with
     * all instalments due up to today.
     */
    private String frequency;

    /** #1/#5: entered on the Bond SIP Transaction screen, mirrored back here. */
    private Double actualSipAmount;

    /** #3: CREATED -> INPROGRESS when it executes (#4). Disabled field all the time. */
    private String status;
}
