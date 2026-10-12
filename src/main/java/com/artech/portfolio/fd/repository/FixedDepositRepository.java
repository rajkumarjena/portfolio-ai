package com.artech.portfolio.fd.repository;

import com.artech.portfolio.fd.entity.FixedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {
}
