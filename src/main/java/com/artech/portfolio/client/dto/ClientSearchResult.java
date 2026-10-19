package com.artech.portfolio.client.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * BONDBP_V5 #4.b/#4.c: shallow view of a registered client returned by the
 * family-member type-ahead (/api/clients/search). The panel shows exactly
 * these read-only fields after the user picks a match.
 */
@Data
public class ClientSearchResult {
    private Long id;
    private String name;
    private String pan;
    private List<String> emails = new ArrayList<>();
    private List<String> mobileNumbers = new ArrayList<>();
}
