package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.CashFlowData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface CashFlowDataRepository extends JpaRepository<CashFlowData, Long> {

    // Query by bond id
    List<CashFlowData> findByBondId(Long bondId);

    // Query by year
    List<CashFlowData> findByYear(int year);

    // Query by exact date
    List<CashFlowData> findByDate(LocalDate date);

    // Custom query for date range
    @Query("SELECT c FROM CashFlowData c WHERE c.date BETWEEN :startDate AND :endDate")
    List<CashFlowData> findByDateRange(LocalDate startDate, LocalDate endDate);
}
