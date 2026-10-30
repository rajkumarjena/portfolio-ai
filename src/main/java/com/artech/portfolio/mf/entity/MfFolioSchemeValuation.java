package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfValuationBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Accepted valuation of a scheme holding (MF.md 7e/11).
 * {@link com.artech.portfolio.mf.entity.tmp.MfTmpValuation} is its temporary twin.
 */
@Entity
@Table(name = "mf_folio_schemes_valuation")
public class MfFolioSchemeValuation extends MfValuationBase {
}
