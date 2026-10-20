package com.artech.portfolio.summary.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V6 #3: one row of the widget table exactly as the sample report
 * defines it - the first column is the Bond Return caption, then the Self
 * and the Family figure.
 *
 * #6: every number and every date boundary is already computed by the
 * server; the front-end only formats what it is given.
 */
@Data
public class WidgetRow {

    /** e.g. "Total Investment", "Return This Week" - the sample's left column. */
    private String label;

    /** INVESTMENT | WEEK | MONTH | QUARTER | YEAR - machine hint for future consumers. */
    private String period;

    /**
     * BONDBP_V6 #5: period range "from current date till the calendar end of
     * the period" (e.g. 2026-10-20 .. 2026-10-25 for the current week).
     * Null for Total Investment, which is a point-in-time figure.
     */
    private LocalDate from;
    private LocalDate to;

    /** BONDBP_V6 #4: return of the logged in user. */
    private double self;

    /** BONDBP_V6 #4: sum of the client and his/her associated family members. */
    private double family;
}
