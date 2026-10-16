package com.artech.portfolio.mf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDate;

/**
 * Registered mutual fund scheme master (MF.md 20).
 * One row per (scheme name, AMC). Imported schemes that do not exist here are
 * flagged NEW_SCHEME in the reconciliation report until the user remaps them or
 * hits Accept, which registers them.
 */
@Entity
@Data
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"scheme_name", "amc_name"}))
public class MfScheme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_name")
    private String schemeName;

    @Column(name = "amc_name")
    private String amcName;

    private String category;

    private String notes;

    private LocalDate createdAt = LocalDate.now();
}
