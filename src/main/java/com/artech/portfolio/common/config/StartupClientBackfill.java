package com.artech.portfolio.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * CONSISTENCY.md 6: investment rows (FD, bond) must carry a client id. New
 * rows always do - the services bind the signed-in client on save. This runner
 * only deals with rows that pre-date that rule: fixed_deposit / bond carry no
 * owner trace at all, so any leftovers are assigned to the earliest registered
 * client instead of being dropped or left invisible.
 *
 * <p>Mutual fund ownership moved to the {@code owner_pan} column of the
 * normalized tables (MF_V1+), so the old {@code mf_header} backfill is gone
 * together with that table (MFR_V9 8) - keeping it only produced a
 * "no such table: mf_header" warning on every startup.</p>
 *
 * It runs once per startup, is idempotent (only touches NULL client_id rows)
 * and skips a table that does not exist.
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
        long fd = update("fixed_deposit", """
                UPDATE fixed_deposit SET client_id = (SELECT MIN(id) FROM client)
                WHERE client_id IS NULL""");
        long bond = update("bond", """
                UPDATE bond SET client_id = (SELECT MIN(id) FROM client)
                WHERE client_id IS NULL""");

        if (fd + bond > 0) {
            log.info("Client backfill: {} fixed_deposit, {} bond legacy rows assigned", fd, bond);
        }
    }

    private int update(String table, String sql) {
        try {
            return jdbc.update(sql);
        } catch (RuntimeException e) {
            log.warn("Client backfill skipped {}: {}", table, e.getMessage());
            return 0;
        }
    }
}
