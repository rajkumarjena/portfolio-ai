package com.artech.portfolio.summary.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * BONDBP_V6 #1/#2: payload of the Bonds widget - the first card of the
 * portfolio-summary strip that sits on top of the dashboard. Future widgets
 * (FD, Shares, Mutual Funds, ...) get their own endpoint and view next to it.
 */
@Data
public class BondWidgetView {

    /** "Current date" every period range starts from (BONDBP_V6 #5). */
    private LocalDate asOf;

    /** The five sample rows, in sample order. */
    private List<WidgetRow> rows = new ArrayList<>();
}
