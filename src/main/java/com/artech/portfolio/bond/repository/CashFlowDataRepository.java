package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.BondCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface CashFlowDataRepository extends JpaRepository<BondCashFlow, Long> {

    // Query by bond id
    List<BondCashFlow> findByBondId(Long bondId);

    // Query by year
    List<BondCashFlow> findByYear(int year);

    // Query by exact date
    List<BondCashFlow> findByDate(LocalDate date);

    // Custom query for date range
    @Query("SELECT c FROM BondCashFlow c WHERE c.date BETWEEN :startDate AND :endDate")
    List<BondCashFlow> findByDateRange(LocalDate startDate, LocalDate endDate);
}
