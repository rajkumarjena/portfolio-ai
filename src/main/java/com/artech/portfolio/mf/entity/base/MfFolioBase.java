package com.artech.portfolio.mf.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * One folio of the investor's statement (MF.md 7b) - the account number a set
 * of schemes is held under, with the KYC details printed under it.
 *
 * <p>Mapped onto both the permanent and the temporary table set (MF.md 7/9).</p>
 */
@Data
@MappedSuperclass
public abstract class MfFolioBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    /** Folio number exactly as printed ("16988987/ 40"). */
    @Column(name = "folio_no", nullable = false, length = 40)
    private String folioNo;

    /** Foreign key to the normalized AMC master row. */
    @Column(name = "amc_id")
    private Long amcId;

    /** Display-only compatibility value; the database stores only amc_id. */
    @Transient
    private String amc;

    /** Report source that last refreshed this snapshot row. */
    @Column(name = "refresh_from", length = 30)
    private String refreshFrom;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "amc_id", insertable = false, updatable = false,
            foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private com.artech.portfolio.mf.entity.MfAmc amcReference;

    public String getAmc() {
        return amc != null ? amc : (amcReference == null ? null : amcReference.getName());
    }

    public void setAmc(String amc) {
        this.amc = amc;
    }

    /** Investor name printed under the folio header. */
    @Column(name = "investor_name", length = 120)
    private String investorName;

    /** PAN held against the folio - not the owner's login PAN. */
    @Column(name = "folio_pan", length = 20)
    private String folioPan;

    @Column(name = "kyc", length = 40)
    private String kyc;

    @Column(name = "pan_kyc", length = 40)
    private String panKyc;
}
