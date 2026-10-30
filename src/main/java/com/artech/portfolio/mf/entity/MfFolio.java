package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfFolioBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Accepted folio of the last reconciled KFINTECH statement (MF.md 7b).
 * {@link com.artech.portfolio.mf.entity.tmp.MfTmpFolio} is its temporary twin.
 */
@Entity
@Table(name = "mf_folios")
public class MfFolio extends MfFolioBase {
    /** One folio may contain multiple scheme holdings. */
    @OneToMany(mappedBy = "folioReference", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MfFolioScheme> schemes = new ArrayList<>();
}
