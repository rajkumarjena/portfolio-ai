package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MfHeaderRepository extends JpaRepository<MfHeader, Long> {

    List<MfHeader> findByPanOrderByCreatedAtDesc(String pan);

    // List-returning on purpose: Optional finders render as "fetch first ? rows
    // only", which the project's SQLiteDialect cannot translate (ClientRepository).
    List<MfHeader> findByPanAndSchemeIdAndFolioNoIgnoreCase(String pan, Long schemeId, String folioNo);

    /** Fallback when the report row carries no folio number. */
    List<MfHeader> findByPanAndSchemeId(String pan, Long schemeId);

    List<MfHeader> findByPanAndRegistrationTypeOrderBySipStartDate(String pan, String registrationType);

    /** SIPs whose auto-execution window is still open (MF.md 7). */
    @Query("""
            select h from MfHeader h
            where h.registrationType = 'SIP'
              and h.status = 'ACTIVE'
              and h.sipStartDate is not null
              and (h.sipEndDate is null or h.sipEndDate >= :today)
            """)
    List<MfHeader> findDueActiveSips(@Param("today") LocalDate today);

    /**
     * Portfolio search for the MF view screen (MF.md 18/19): optional date range
     * on registration date, AMC, category and free text across scheme / folio / notes.
     */
    @Query("""
            select h from MfHeader h join h.scheme s
            where h.pan = :pan
              and (:from is null or h.createdAt >= :from)
              and (:to is null or h.createdAt <= :to)
              and (:amc is null or lower(s.amcName) = lower(:amc))
              and (:category is null or lower(s.category) = lower(:category))
              and (:status is null or h.status = :status)
              and (:q is null or lower(s.schemeName) like lower(concat('%', :q, '%'))
                               or lower(s.amcName) like lower(concat('%', :q, '%'))
                               or lower(h.folioNo) like lower(concat('%', :q, '%'))
                               or lower(h.notes) like lower(concat('%', :q, '%')))
            order by h.createdAt desc
            """)
    List<MfHeader> search(@Param("pan") String pan,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("amc") String amc,
                          @Param("category") String category,
                          @Param("status") String status,
                          @Param("q") String q);
}
