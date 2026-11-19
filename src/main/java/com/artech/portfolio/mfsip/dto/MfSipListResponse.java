package com.artech.portfolio.mfsip.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The SIP list screen's envelope: the rows plus the summary cards, so the page
 * never has to add figures up itself (MF.md 18).
 */
@Data
public class MfSipListResponse {
    private List<MfSipView> rows = new ArrayList<>();
    private Map<String, Object> summary = new LinkedHashMap<>();
}
