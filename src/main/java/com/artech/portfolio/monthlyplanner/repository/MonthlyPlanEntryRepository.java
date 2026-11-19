package com.artech.portfolio.monthlyplanner.repository;

import com.artech.portfolio.monthlyplanner.entity.MonthlyPlanEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyPlanEntryRepository extends JpaRepository<MonthlyPlanEntry, Long> {

    /** One month of one client - at most one row per category (unique key). */
    List<MonthlyPlanEntry> findByClientIdAndPlanMonthOrderByCategoryAsc(Long clientId, LocalDate planMonth);

    /**
     * MP_V1 #4: every row up to and including the month, oldest first. One query
     * serves both the month itself and the recurring figures it inherits from.
     */
    List<MonthlyPlanEntry> findByClientIdAndPlanMonthLessThanEqualOrderByPlanMonthAsc(
            Long clientId, LocalDate planMonth);
}
