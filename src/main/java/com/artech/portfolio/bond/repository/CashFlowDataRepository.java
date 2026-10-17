package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.BondCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CashFlowDataRepository extends JpaRepository<BondCashFlow, Long> {

    // Query by bond id
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE b.id = :bondId")
    List<BondCashFlow> findByBondId(Long bondId);

    // Query by year
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE c.year = :year")
    List<BondCashFlow> findByYear(int year);

    // Query by exact date
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE c.date = :date")
    List<BondCashFlow> findByDate(LocalDate date);

    // Custom query for date range
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE c.date BETWEEN :startDate AND :endDate")
    List<BondCashFlow> findByDateRange(LocalDate startDate, LocalDate endDate);

    // ---------------------------------------------------------------------
    // BONDBP B.3: per-user scoping. A non-admin only ever reads cash flows of
    // bonds they own; ADMIN uses the unscoped finders above. One join, done
    // in SQL - no lazy-loading N+1 per row.
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE b.client.id = :clientId")
    List<BondCashFlow> findAllForClient(@Param("clientId") Long clientId);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client "
            + "WHERE b.client.id = :clientId AND c.year = :year")
    List<BondCashFlow> findByYearForClient(@Param("clientId") Long clientId,
                                           @Param("year") int year);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client "
            + "WHERE b.client.id = :clientId AND c.date = :date")
    List<BondCashFlow> findByDateForClient(@Param("clientId") Long clientId,
                                           @Param("date") LocalDate date);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE b.client.id = :clientId "
            + "AND c.date BETWEEN :startDate AND :endDate")
    List<BondCashFlow> findByDateRangeForClient(@Param("clientId") Long clientId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client "
            + "WHERE b.client.id = :clientId AND b.id = :bondId")
    List<BondCashFlow> findByBondForClient(@Param("clientId") Long clientId,
                                           @Param("bondId") Long bondId);
}
