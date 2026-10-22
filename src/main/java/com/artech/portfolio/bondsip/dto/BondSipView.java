package com.artech.portfolio.bondsip.dto;

import com.artech.portfolio.bondsip.entity.BondSip;
import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V9 #1 lean Bond SIP screen row: Issuer, Investment Date (#3, was
 * Create Date), SIP Date, SIP Amount, Frequency, Status. The former Actual
 * SIP Amount / Bond Name columns went away with the Bond SIP Transaction
 * screen - instalments are bond rows now, visible on the Bond Records screen.
 */
@Data
public class BondSipView {
    private Long id;
    private String issuer;
    private LocalDate investmentDate;
    private LocalDate sipDate;
    private double sipAmount;
    private String frequency;
    private String status;

    public static BondSipView of(BondSip sip) {
        BondSipView view = new BondSipView();
        view.setId(sip.getId());
        view.setIssuer(sip.getIssuer());
        view.setInvestmentDate(sip.getInvestmentDate());
        view.setSipDate(sip.getSipDate());
        view.setSipAmount(sip.getSipAmount());
        view.setFrequency(sip.getFrequency());
        view.setStatus(sip.getStatus());
        return view;
    }
}
