package com.artech.portfolio.mf.entity.tmp;

import com.artech.portfolio.mf.entity.base.MfNomineeBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Temporary nominee of the statement under reconciliation (MF.md 7d/9). */
@Entity
@Table(name = "mf_tmp_folio_schemes_nominees")
public class MfTmpNominee extends MfNomineeBase {
}
