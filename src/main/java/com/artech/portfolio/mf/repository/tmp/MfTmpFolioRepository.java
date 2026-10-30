package com.artech.portfolio.mf.repository.tmp;

import com.artech.portfolio.mf.entity.tmp.MfTmpFolio;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Temporary folios (MF.md 7b/9). */
public interface MfTmpFolioRepository extends JpaRepository<MfTmpFolio, Long> {

    List<MfTmpFolio> findByOwnerPanOrderByFolioNo(String ownerPan);

    @Modifying
    @Transactional
    @Query("delete from MfTmpFolio f where f.ownerPan = :pan")
    int deleteByOwner(@Param("pan") String pan);
}
