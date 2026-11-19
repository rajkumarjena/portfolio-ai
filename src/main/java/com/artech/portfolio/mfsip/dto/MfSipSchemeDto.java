package com.artech.portfolio.mfsip.dto;

import lombok.Data;

/** The scheme identity of a registration (and of the entry screen's datalists). */
@Data
public class MfSipSchemeDto {
    private String schemeName;
    private String amcName;
    private String category;

    public static MfSipSchemeDto of(String schemeName, String amcName, String category) {
        MfSipSchemeDto dto = new MfSipSchemeDto();
        dto.setSchemeName(schemeName);
        dto.setAmcName(amcName);
        dto.setCategory(category);
        return dto;
    }
}
