package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.BondCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CashFlowDataRepository extends JpaRepository<BondCashFlow, Long> {

    // ---------------------------------------------------------------------
    // BONDBP_V4 #1: the Bond Returns Report always displays records ordered
    // by Payment Date ascending (default display pattern). The ordering lives
    // here, in SQL, so every /api/cashflows endpoint returns it - the UI just
    // renders whatever the server sends (BONDBP_V4 #5: logic on server side),
    // and the report's in-memory filters preserve the server order.
    // id breaks same-day ties deterministically.

    // Query by bond id
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE b.id = :bondId"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByBondId(Long bondId);

    // Query by year
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE c.year = :year"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByYear(int year);

    // Query by exact date
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE c.date = :date"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByDate(LocalDate date);

    // Custom query for date range
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE c.date BETWEEN :startDate AND :endDate"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByDateRange(LocalDate startDate, LocalDate endDate);

    // ---------------------------------------------------------------------
    // BONDBP B.3: per-user scoping. A non-admin only ever reads cash flows of
    // bonds they own; ADMIN uses the unscoped finders above. One join, done
    // in SQL - no lazy-loading N+1 per row.
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE b.client.id = :clientId"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findAllForClient(@Param("clientId") Long clientId);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client "
            + "WHERE b.client.id = :clientId AND c.year = :year"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByYearForClient(@Param("clientId") Long clientId,
                                           @Param("year") int year);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client "
            + "WHERE b.client.id = :clientId AND c.date = :date"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByDateForClient(@Param("clientId") Long clientId,
                                           @Param("date") LocalDate date);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client WHERE b.client.id = :clientId "
            + "AND c.date BETWEEN :startDate AND :endDate"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByDateRangeForClient(@Param("clientId") Long clientId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b JOIN FETCH b.client "
            + "WHERE b.client.id = :clientId AND b.id = :bondId"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findByBondForClient(@Param("clientId") Long clientId,
                                           @Param("bondId") Long bondId);

    // ---------------------------------------------------------------------
    // BONDBP_V6 #4/#4.c-#4.d: one scope (Self, or Family = the client plus
    // his/her associated family members) in one query - bond joined eagerly
    // so the service can net redemptions per bond without a lazy N+1.
    // Deliberately List-returning with no Pageable: Spring Data would render
    // "fetch first ? rows only", which this project's SQLiteDialect cannot
    // translate (same note as in ClientRepository).
    @Query("SELECT c FROM BondCashFlow c JOIN FETCH c.bond b WHERE b.client.id IN :clientIds"
            + " ORDER BY c.date ASC, c.id ASC")
    List<BondCashFlow> findAllForClients(@Param("clientIds") Collection<Long> clientIds);
}
