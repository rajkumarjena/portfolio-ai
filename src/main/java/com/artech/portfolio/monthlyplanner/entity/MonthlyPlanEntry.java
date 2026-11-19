package com.artech.portfolio.monthlyplanner.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

@Entity
@Table(name = "monthly_plan_entry",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_monthly_plan_client_month_category",
                columnNames = {"client_id", "plan_month", "category"}))
@Data
public class MonthlyPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    private String category;

    @Column(name = "plan_month")
    private LocalDate planMonth;

    private double amount;

    private boolean recurring;

    private LocalDate startDate;

    private LocalDate endDate;

    private String frequency;

    private String source;

    private String note;

    /** MP_V2 #6: whether the bill/obligation of this spending row has been paid. */
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean paid;
}