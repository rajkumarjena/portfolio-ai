package com.artech.portfolio.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * CONSISTENCY.md 6: investment headers (FD, bond, mutual fund) must carry a
 * client id. New rows always do - the services bind the signed-in client on
 * save. This runner only deals with rows that pre-date that rule:
 *
 *   mf_header      -> matched to the client with the same PAN (MF.md 4 keeps
 *                     the PAN on every header, so the match is exact);
 *   fixed_deposit / bond -> carry no owner trace at all, so any leftovers are
 *                     assigned to the earliest registered client instead of
 *                     being dropped or left invisible.
 *
 * It runs once per startup, is idempotent (only touches NULL client_id rows)
 * and is a no-op on a fresh database.
 */
@Component
public class StartupClientBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupClientBackfill.class);

    private final JdbcTemplate jdbc;

    public StartupClientBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        long mf = update("""
                UPDATE mf_header SET client_id =
                    (SELECT c.id FROM client c WHERE UPPER(c.pan) = UPPER(mf_header.pan))
                WHERE client_id IS NULL""");
        long orphanMf = update("""
                UPDATE mf_header SET client_id = (SELECT MIN(id) FROM client)
                WHERE client_id IS NULL""");

        long fd = update("""
                UPDATE fixed_deposit SET client_id = (SELECT MIN(id) FROM client)
                WHERE client_id IS NULL""");
        long bond = update("""
                UPDATE bond SET client_id = (SELECT MIN(id) FROM client)
                WHERE client_id IS NULL""");

        if (mf + orphanMf + fd + bond > 0) {
            log.info("Client backfill: {} mf_header (by PAN), {} mf_header (fallback), "
                            + "{} fixed_deposit, {} bond legacy rows assigned",
                    mf, orphanMf, fd, bond);
        }
    }

    private int update(String sql) {
        try {
            return jdbc.update(sql);
        } catch (RuntimeException e) {
            // Table missing on a brand-new database before DDL, or no clients yet
            log.warn("Client backfill skipped a table: {}", e.getMessage());
            return 0;
        }
    }
}
