package com.artech.portfolio.mf.entity.tmp;

import com.artech.portfolio.mf.entity.base.MfValuationBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Temporary valuation of the statement under reconciliation (MF.md 7e/9). */
@Entity
@Table(name = "mf_tmp_folio_schemes_valuation")
public class MfTmpValuation extends MfValuationBase {
}
