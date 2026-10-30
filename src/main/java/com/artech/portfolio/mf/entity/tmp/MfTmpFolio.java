package com.artech.portfolio.mf.entity.tmp;

import com.artech.portfolio.mf.entity.base.MfFolioBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Temporary folio of the statement under reconciliation (MF.md 7b/9). */
@Entity
@Table(name = "mf_tmp_folios")
public class MfTmpFolio extends MfFolioBase {
}
