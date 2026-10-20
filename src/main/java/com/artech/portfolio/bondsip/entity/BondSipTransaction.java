package com.artech.portfolio.bondsip.entity;

import com.artech.portfolio.bond.entity.Bond;
import jakarta.persistence.*;

import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V7 #4/#5: a Bond SIP Transaction record is created automatically the
 * day a due Bond SIP executes (status INPROGRESS). Issuer, Create Date, SIP
 * Date and SIP Amount are copied from the parent SIP and are never editable
 * (#5); the user enters the Actual SIP Amount and associates a Bond Name
 * (#6), after which the transaction turns EXECUTED (#7).
 */
@Entity
@Data
public class BondSipTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The SIP this transaction was auto-created from (#4). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bond_sip_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private BondSip bondSip;

    /** Copied snapshot - #5: these four never change from the UI. */
    private String issuer;
    private LocalDate createDate;
    private LocalDate sipDate;
    private double sipAmount;

    /** #5: the only amount the user may enter. */
    private Double actualSipAmount;

    /** #6/#7: the associated bond (child of the Bond master); null until picked. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bond_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Bond bond;

    /** #4: created as INPROGRESS; #7: becomes EXECUTED once the bond is associated. */
    private String status;

    /** BONDBP_V8 #4: true if this transaction was created by the reconciliation/adjust process. */
    private Boolean reconciliation;
}
