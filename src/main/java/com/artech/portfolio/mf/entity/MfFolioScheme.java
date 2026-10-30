package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfSchemeBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Getter;

/**
 * Accepted scheme holding of the last reconciled KFINTECH statement
 * (MF.md 7c). {@link com.artech.portfolio.mf.entity.tmp.MfTmpScheme} is its
 * temporary twin.
 */
@Entity
@Table(name = "mf_folio_schemes")
@Getter
public class MfFolioScheme extends MfSchemeBase {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "folio_id", insertable = false, updatable = false,
            foreignKey = @jakarta.persistence.ForeignKey(jakarta.persistence.ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MfFolio folioReference;
}
