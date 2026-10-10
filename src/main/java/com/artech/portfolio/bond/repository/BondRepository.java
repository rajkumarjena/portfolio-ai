package com.artech.portfolio.bond.repository;

import com.artech.portfolio.bond.entity.Bond;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondRepository extends JpaRepository<Bond, Long> {
}
