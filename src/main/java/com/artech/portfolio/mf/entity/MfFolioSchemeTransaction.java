package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfTxnBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Accepted ledger row of a scheme holding (MF.md 7g) - the ADJUSTED rows of
 * the first import live here too (MF.md 10).
 * {@link com.artech.portfolio.mf.entity.tmp.MfTmpTransaction} is its temporary twin.
 */
@Entity
@Table(name = "mf_folio_scheme_transactions")
public class MfFolioSchemeTransaction extends MfTxnBase {
}
