package com.artech.portfolio.bondsip.dto;

import lombok.Data;

import java.util.List;

/**
 * BOND_V11 #4/#5: the Sync Now payload - the clients an ADMIN picked in the
 * Bond SIP screen's multi-select. Empty (or omitted) means "the signed-in
 * client only"; a non-admin's ids are ignored server-side.
 */
@Data
public class ReconcileBondSipRequest {
    private List<Long> clientIds;
}
