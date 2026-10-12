package com.artech.portfolio.common.repository;

import com.artech.portfolio.common.entity.AppParamConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppParamRepository extends JpaRepository<AppParamConfig, Long> {
    List<AppParamConfig> findAll();

    List<AppParamConfig> findByInvestmentTypeAndParamName(String investmentType, String paramName);
}
