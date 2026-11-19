package com.artech.portfolio.monthlyplanner.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MP_V1 - the Save payload of one planner screen: the month ("yyyy-MM") and every
 * figure typed on it. Categories the server auto-populates from another module
 * are ignored (they are read only by design, MP_V1 #5).
 */
@Data
public class SavePlannerRequest {
    private String month;
    private List<PlannerEntryRequest> entries = new ArrayList<>();
}
