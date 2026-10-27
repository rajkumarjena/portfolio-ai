package com.artech.portfolio.mf.entity;

import com.artech.portfolio.mf.entity.base.MfFolioBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Accepted folio of the last reconciled KFINTECH statement (MF.md 7b).
 * {@link com.artech.portfolio.mf.entity.tmp.MfTmpFolio} is its temporary twin.
 */
@Entity
@Table(name = "mf_folios")
public class MfFolio extends MfFolioBase {
}
