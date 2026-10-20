package com.artech.portfolio.bondsip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BONDBP_V7 #6 one entry of the Bond Name drop-down: the bond's id plus the
 * display name "Bond Name|PAN" so the same bond held by several family
 * members stays identifiable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BondSipBondOption {
    private Long bondId;
    private String label;
}
