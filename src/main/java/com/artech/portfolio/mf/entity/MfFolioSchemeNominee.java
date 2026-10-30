package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfNomineeBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Accepted nominee of a scheme holding (MF.md 7d).
 * {@link com.artech.portfolio.mf.entity.tmp.MfTmpNominee} is its temporary twin.
 */
@Entity
@Table(name = "mf_folio_schemes_nominees")
public class MfFolioSchemeNominee extends MfNomineeBase {
}
