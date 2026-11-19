package com.artech.portfolio.monthlyplanner.repository;

import com.artech.portfolio.monthlyplanner.entity.MonthlyPlanHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyPlanHistoryRepository extends JpaRepository<MonthlyPlanHistory, Long> {

    /** The captured figures of one month - what the snapshot upserts against. */
    List<MonthlyPlanHistory> findByClientIdAndPlanMonthOrderByCategoryAsc(
            Long clientId, LocalDate planMonth);

    /** Every captured figure of a window, for the analytics that will read it. */
    List<MonthlyPlanHistory> findByClientIdAndPlanMonthBetweenOrderByPlanMonthAscCategoryAsc(
            Long clientId, LocalDate from, LocalDate to);
}
