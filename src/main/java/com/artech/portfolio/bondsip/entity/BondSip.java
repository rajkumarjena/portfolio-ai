package com.artech.portfolio.bondsip.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V7/V9 - Bond SIP (#1): Issuer, Investment Date, SIP Date, SIP Amount,
 * Frequency, Status.
 *
 * BONDBP_V9 #3: the old "Create Date" is renamed "Investment Date", both on
 * the screen and in the database column, and is what a bond row auto-created
 * from an instalment carries as its Investment Date (#4.b).
 *
 * BONDBP_V9 #4: there is no separate transaction table any more - each due
 * instalment becomes a bond row directly (status "In Progress", Investment
 * Type "SIP Investment") and the user completes the remaining details on the
 * Bond Details Entry screen, which turns the row EXECUTED (#4.h).
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

    /** BONDBP_V9 #3: auto-populated to the current date server-side (was "Create Date"). */
    private LocalDate investmentDate;

    /** #2: mandatory - the calendar date each instalment comes due. */
    private LocalDate sipDate;

    /** #2: mandatory planned amount - becomes Investment Amount Per Unit of each bond row (#4.d). */
    private double sipAmount;

    /**
     * Frequency of the instalments (like a bond's coupon frequency):
     * WEEKLY / MONTHLY / QUARTERLY / HALF-YEARLY / YEARLY - mapped onto the
     * bond row's Coupon Frequency (#4.e). A SIP entered with a past start date
     * is backfilled with all instalments due up to today.
     */
    private String frequency;

    /** #3: CREATED -> INPROGRESS when the first instalment is generated (#4). Disabled field all the time. */
    private String status;
}
