package com.artech.portfolio.mf.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.FetchType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/** Normalized mutual-fund house reference shared by folios and summaries. */
@Entity
@Table(name = "mf_amc")
@Data
public class MfAmc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    /** One fund house owns many investor folios; the actual name is normalized once here. */
    @JsonIgnore
    @OneToMany(mappedBy = "amcReference", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MfFolio> folios = new ArrayList<>();

    /** AMC-level detail statement summary rows; total rows have no AMC. */
    @JsonIgnore
    @OneToMany(mappedBy = "amcReference", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MfPortfolioSummary> summaries = new ArrayList<>();

    /** Summary-statement rows are staged with the upload batch. */
    @JsonIgnore
    @OneToMany(mappedBy = "amcReference", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MfSummaryReconciliationLine> summaryReconciliationLines = new ArrayList<>();
}
