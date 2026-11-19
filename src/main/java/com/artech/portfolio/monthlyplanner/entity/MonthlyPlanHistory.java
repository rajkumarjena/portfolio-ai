package com.artech.portfolio.monthlyplanner.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_plan_history",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_monthly_plan_history_client_month_category",
                columnNames = {"client_id", "plan_month", "category"}))
@Data
public class MonthlyPlanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    @Column(name = "plan_month")
    private LocalDate planMonth;

    private String category;

    private String kind;

    private double amount;

    private double entryAmount;

    private int occurrences;

    private String frequency;

    private boolean recurring;

    private String source;

    private LocalDateTime capturedAt;

    /** MP_V2 #6: whether the bill/obligation of this spending row was paid. */
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean paid;
}