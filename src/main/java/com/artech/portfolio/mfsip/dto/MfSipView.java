package com.artech.portfolio.mfsip.dto;

import com.artech.portfolio.mfsip.entity.MfSipRegistration;
import lombok.Data;

import java.time.LocalDate;

/** One row of the Mutual Fund SIP list / the entry screen's edit mode. */
@Data
public class MfSipView {
    private Long id;
    private MfSipSchemeDto scheme;
    private String folioNo;
    private String registrationType;
    private String sipType;
    private double sipAmount;
    private int sipDate;
    private LocalDate sipStartDate;
    private LocalDate sipEndDate;
    private String bankName;
    private String bankAccountNumber;
    private String status;
    private String notes;
    private double nav;
    private double units;
    private double investedValue;
    private double currentValue;
    private LocalDate lastExecutedOn;

    public static MfSipView of(MfSipRegistration reg) {
        MfSipView view = new MfSipView();
        view.setId(reg.getId());
        view.setScheme(MfSipSchemeDto.of(reg.getSchemeName(), reg.getAmcName(), reg.getCategory()));
        view.setFolioNo(reg.getFolioNo());
        view.setRegistrationType(reg.getRegistrationType());
        view.setSipType(reg.getSipType());
        view.setSipAmount(reg.getSipAmount());
        view.setSipDate(reg.getSipDay());
        view.setSipStartDate(reg.getSipStartDate());
        view.setSipEndDate(reg.getSipEndDate());
        view.setBankName(reg.getBankName());
        view.setBankAccountNumber(reg.getBankAccountNumber());
        view.setStatus(reg.getStatus());
        view.setNotes(reg.getNotes());
        view.setNav(reg.getNav());
        view.setUnits(reg.getUnits());
        view.setInvestedValue(reg.getInvestedValue());
        view.setCurrentValue(reg.getCurrentValue());
        view.setLastExecutedOn(reg.getLastExecutedOn());
        return view;
    }
}
