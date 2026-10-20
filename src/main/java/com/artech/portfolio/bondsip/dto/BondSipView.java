package com.artech.portfolio.bondsip.dto;

import com.artech.portfolio.bondsip.entity.BondSip;
import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V7 #1 screen row: Issuer, Create Date, SIP Date, SIP Amount,
 * Frequency, Actual SIP Amount, Bond Name, Status - exactly the columns of the
 * Bond SIP screen, with Bond Name derived from the transaction created at
 * execution.
 */
@Data
public class BondSipView {
    private Long id;
    private String issuer;
    private LocalDate createDate;
    private LocalDate sipDate;
    private double sipAmount;
    private String frequency;
    private Double actualSipAmount;
    private String bondName;
    private String status;

    public static BondSipView of(BondSip sip, String bondName) {
        BondSipView view = new BondSipView();
        view.setId(sip.getId());
        view.setIssuer(sip.getIssuer());
        view.setCreateDate(sip.getCreateDate());
        view.setSipDate(sip.getSipDate());
        view.setSipAmount(sip.getSipAmount());
        view.setFrequency(sip.getFrequency());
        view.setActualSipAmount(sip.getActualSipAmount());
        view.setBondName(bondName);
        view.setStatus(sip.getStatus());
        return view;
    }
}
