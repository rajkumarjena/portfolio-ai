package com.artech.portfolio.mf.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

/**
 * A nominee printed under a scheme block (MF.md 7d).
 *
 * <p>Mapped onto both the permanent and the temporary table set (MF.md 7/9).</p>
 */
@Data
@MappedSuperclass
public abstract class MfNomineeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_id", nullable = false)
    private Long schemeId;

    @Column(name = "owner_pan", nullable = false, length = 20)
    private String ownerPan;

    @Column(name = "nominee_name", length = 120)
    private String nomineeName;
}
