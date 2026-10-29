package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfAmc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfAmcRepository extends JpaRepository<MfAmc, Long> {
    List<MfAmc> findByNormalizedName(String normalizedName);
}
