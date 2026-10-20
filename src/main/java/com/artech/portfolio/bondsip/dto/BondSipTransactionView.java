package com.artech.portfolio.bondsip.dto;

import com.artech.portfolio.bondsip.entity.BondSipTransaction;
import lombok.Data;

import java.time.LocalDate;

/**
 * BONDBP_V7 #5 Bond SIP Transaction view row: Issuer, Create Date, SIP Date
 * and SIP Amount are the immutable snapshot (readonly in the UI), only
 * Actual SIP Amount and the Bond Name dropdown may be changed.
 */
@Data
public class BondSipTransactionView {
    private Long id;
    private String issuer;
    private LocalDate createDate;
    private LocalDate sipDate;
    private double sipAmount;
    private Double actualSipAmount;
    private Long bondId;
    private String bondName;
    private String status;

    public static BondSipTransactionView of(BondSipTransaction tx) {
        BondSipTransactionView view = new BondSipTransactionView();
        view.setId(tx.getId());
        view.setIssuer(tx.getIssuer());
        view.setCreateDate(tx.getCreateDate());
        view.setSipDate(tx.getSipDate());
        view.setSipAmount(tx.getSipAmount());
        view.setActualSipAmount(tx.getActualSipAmount());
        view.setStatus(tx.getStatus());
        if (tx.getBond() != null) {
            view.setBondId(tx.getBond().getId());
            view.setBondName(tx.getBond().getBondName());
        }
        return view;
    }
}
