package com.artech.portfolio.monthlyplanner.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MP_V1 #8 - what the planner resolved for one category of one month, kept as
 * history: every earning and every expense of every month that has happened.
 *
 * <p>This is deliberately a snapshot, not another rule: the recurrence rules are
 * replayed only while planning, so the analytics that will be built later reads
 * one flat row per month and category instead of re-deriving the past from the
 * entries. Future months are projections and are not captured.</p>
 */
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

    // CONSISTENCY.md 6: the history belongs to the signed-in client.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** First day of the month the figures belong to. */
    @Column(name = "plan_month")
    private LocalDate planMonth;

    /** One of MonthlyPlannerConstants.EARNINGS / SPENDING keys. */
    private String category;

    /** EARNING or SPENDING - so an analytics query never has to join the vocabulary. */
    private String kind;

    /** What the category contributed to the month (weekly entries: the month's own weeks). */
    private double amount;

    /** The figure as entered (the weekly figure, or a future-dated entry's value). */
    private double entryAmount;

    /** How many times a weekly entry fell in the month. */
    private int occurrences;

    private String frequency;

    private boolean recurring;

    /** MANUAL, or AUTO when another module supplied the figure. */
    private String source;

    /** When this month's snapshot was last written. */
    private LocalDateTime capturedAt;
}
