package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfFolio;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Accepted folios (MF.md 7b). */
public interface MfFolioRepository extends JpaRepository<MfFolio, Long> {

    List<MfFolio> findByOwnerPanOrderByFolioNo(String ownerPan);

    List<MfFolio> findByOwnerPanAndAmcIdOrderByFolioNo(String ownerPan, Long amcId);

    @Modifying
    @Transactional
    @Query("delete from MfFolio f where f.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
