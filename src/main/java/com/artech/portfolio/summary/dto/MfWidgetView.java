package com.artech.portfolio.summary.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MFR_V11: payload of the Mutual Funds card - the widget that sits right next
 * to the Bonds card (MFR_V11 #4) in the portfolio-summary strip on top of the
 * dashboard. Same strip, same card chrome, its own endpoint.
 */
@Data
public class MfWidgetView {

    /** "Current date" the current month / quarter / financial year are derived from. */
    private LocalDate asOf;

    /** The five sample rows, in sample order (MFR_V11 #2). */
    private List<MfWidgetRow> rows = new ArrayList<>();
}
