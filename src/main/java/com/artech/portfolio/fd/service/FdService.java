package com.artech.portfolio.fd.service;

import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.fd.entity.FixedDeposit;

import java.util.List;

public interface FdService {
    FixedDeposit saveFixedDeposit(FixedDeposit fixedDeposit);

    List<FixedDeposit> getAllFixedDeposits();

    FixedDeposit getFixedDepositById(Long id);

    FixedDeposit updateFixedDeposit(Long id, FixedDeposit fixedDepositDetails);

    void deleteFixedDeposit(Long id);

    List<AppParamConfig> getBankNames();
}
