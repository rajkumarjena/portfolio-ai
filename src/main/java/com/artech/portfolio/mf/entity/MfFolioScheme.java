package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfSchemeBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Accepted scheme holding of the last reconciled KFINTECH statement
 * (MF.md 7c). {@link com.artech.portfolio.mf.entity.tmp.MfTmpScheme} is its
 * temporary twin.
 */
@Entity
@Table(name = "mf_folio_schemes")
public class MfFolioScheme extends MfSchemeBase {
}
