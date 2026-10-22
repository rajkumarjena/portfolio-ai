package com.artech.portfolio.bond.dto;

import com.artech.portfolio.bond.entity.Bond;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * BONDBP_V9 #9/#11 one row of the Bond Records screen, fully resolved on the
 * server: display names for issuer / credit rating, the derived amounts and
 * the new Status + Investment Type columns - the screen only renders what it
 * is given, so every future front-end inherits the same behaviour.
 */
@Data
public class BondRowView {

    private Long id;
    private String bondName;
    /** Issuer as the configured display value, not the app_param_config id. */
    private String issuer;
    private String creditRating;
    private LocalDate investmentDate;
    private LocalDate maturityDate;
    private int units;
    /** Investment Amount Per Unit x Units (#9 column). */
    private double totalInvestment;
    /** Sum of the bond's cash-flow payouts + redemption amounts. */
    private double maturityAmount;
    private String clientName;
    private String clientPan;
    /** BONDBP_V9 #2: "In Progress" or "EXECUTED". */
    private String status;
    /** BONDBP_V9 #2: "One Time Investment" or "SIP Investment". */
    private String investmentType;

    public static BondRowView of(Bond bond, Map<String, String> configValues) {
        BondRowView row = new BondRowView();
        row.setId(bond.getId());
        row.setBondName(bond.getBondName());
        row.setIssuer(resolve(configValues, bond.getIssuer()));
        row.setCreditRating(resolve(configValues, bond.getCreditRating()));
        row.setInvestmentDate(bond.getInvestmentDate());
        row.setMaturityDate(bond.getMaturityDate());
        row.setUnits(bond.getUnits());
        row.setTotalInvestment(bond.getInvestmentAmountPerUnit() * bond.getUnits());
        row.setMaturityAmount(bond.getCashFlowData() == null ? 0.0
                : bond.getCashFlowData().stream()
                    .mapToDouble(cf -> cf.getPayout() + cf.getAmount())
                    .sum());
        if (bond.getClient() != null) {
            row.setClientName(bond.getClient().getName());
            row.setClientPan(bond.getClient().getPan());
        }
        row.setStatus(bond.getStatus());
        row.setInvestmentType(bond.getInvestmentType());
        return row;
    }

    /**
     * Config-backed dropdowns (issuer, credit rating) store the app_param_config
     * row id; the screen shows the configured text. Legacy rows hold raw text,
     * which is shown as-is.
     */
    private static String resolve(Map<String, String> configValues, String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        return configValues.getOrDefault(stored, stored);
    }
}
