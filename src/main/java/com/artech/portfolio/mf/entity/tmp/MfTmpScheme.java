package com.artech.portfolio.mf.entity.tmp;

import com.artech.portfolio.mf.entity.base.MfSchemeBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Temporary scheme holding of the statement under reconciliation (MF.md 7c/9). */
@Entity
@Table(name = "mf_tmp_folio_schemes")
public class MfTmpScheme extends MfSchemeBase {
}
