package com.artech.portfolio.mf.repository;

import com.artech.portfolio.mf.entity.MfScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MfSchemeRepository extends JpaRepository<MfScheme, Long> {

    // List-returning on purpose: Optional finders render as "fetch first ? rows
    // only", which the project's SQLiteDialect cannot translate (ClientRepository).
    List<MfScheme> findBySchemeNameIgnoreCaseAndAmcNameIgnoreCase(String schemeName, String amcName);

    /** Ambiguity check: same scheme name under several AMCs needs an explicit remap. */
    List<MfScheme> findBySchemeNameIgnoreCase(String schemeName);

    /** Distinct AMC list for the front-end datalists. */
    @Query("select distinct s.amcName from MfScheme s order by s.amcName")
    List<String> findDistinctAmcs();

    @Query("select distinct s.category from MfScheme s order by s.category")
    List<String> findDistinctCategories();

    @Query("select distinct s.schemeName from MfScheme s order by s.schemeName")
    List<String> findDistinctSchemeNames();
}
