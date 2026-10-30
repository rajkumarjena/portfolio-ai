package com.artech.portfolio.mf.entity.tmp;

import com.artech.portfolio.mf.entity.base.MfTxnBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Temporary ledger row of the statement under reconciliation (MF.md 7g/9) -
 * including the ADJUSTED rows of a first import (MF.md 10).
 */
@Entity
@Table(name = "mf_tmp_folio_scheme_transactions")
public class MfTmpTransaction extends MfTxnBase {
}
