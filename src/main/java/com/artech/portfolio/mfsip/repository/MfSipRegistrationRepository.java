package com.artech.portfolio.mfsip.repository;

import com.artech.portfolio.mfsip.entity.MfSipRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfSipRegistrationRepository extends JpaRepository<MfSipRegistration, Long> {

    /** Per-user scoping (CONSISTENCY.md 6): the SIP list shows the signed-in client's own rows. */
    List<MfSipRegistration> findByClientIdOrderByIdAsc(Long clientId);

    /** The daily execution: every running SIP registration, of every client. */
    List<MfSipRegistration> findByStatusAndRegistrationType(String status, String registrationType);
}
