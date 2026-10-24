package com.artech.portfolio.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class AppParamConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ADMIN_V6 #3/#5: the id of the client who owns this configuration row.
     * Mandatory - it is bound server-side from the signed-in client on save,
     * never taken from the request body. (Existing rows are back-filled by the
     * admin's pre-deployment ALTER TABLE / UPDATE - rule #4 - so no startup
     * migration is needed.)
     */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    private String investmentType;
    private String paramName;
    private String paramValue;
    private String note;
}