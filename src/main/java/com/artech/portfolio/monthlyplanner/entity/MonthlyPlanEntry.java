package com.artech.portfolio.monthlyplanner.entity;

import com.artech.portfolio.client.entity.Client;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * MP_V1 - one planned figure of one month: what the client expects to earn
 * (Salary / Freelance / Side-Hustle) or to spend (the obligation categories).
 *
 * <p>The row is keyed by client + month + category, so a month holds at most one
 * figure per category. Recurrence is carried on the row itself (#4): a row whose
 * {@code recurring} flag is set seeds the following months until the client
 * enters a newer figure. Months with no row and no recurrence resolve to zero -
 * "if user doesn't input it then it will be defaulted to zero".</p>
 */
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

    // CONSISTENCY.md 6: the plan belongs to the signed-in client - bound from the
    // session on save, never from the request body.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Client client;

    /** One of MonthlyPlannerConstants.EARNINGS / SPENDING keys. */
    private String category;

    /** First day of the plan month. */
    @Column(name = "plan_month")
    private LocalDate planMonth;

    private double amount;

    /** MP_V1 #4: carry this figure into the following periods of its frequency. */
    private boolean recurring;

    /**
     * First date the entry applies from - the LIC premium's 29th January, for
     * example. The repeat is anchored on this date's month and a weekly entry
     * repeats on its weekday, so months before it are not counted at all.
     */
    private LocalDate startDate;

    /**
     * Last month the recurrence applies to (first day of that month); null means
     * open ended. Every repeated entry - salary, home loan EMI, LIC, ... - carries
     * one, and the client can edit it later without retyping the amount.
     */
    private LocalDate endDate;

    /** MP_V1 #4: MONTHLY / QUARTERLY / HALF_YEARLY / YEARLY / WEEKLY. */
    private String frequency;

    /** MANUAL, or the module a figure was auto-populated from (MP_V1 #5). */
    private String source;

    private String note;
}
