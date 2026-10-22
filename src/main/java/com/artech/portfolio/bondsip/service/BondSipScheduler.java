package com.artech.portfolio.bondsip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BONDBP_V9 #5 - the automated side of the Bond SIP process. All three hooks
 * share the same idempotent entry point (runDueSips), so they can never create
 * duplicate bond rows:
 *
 *   - every day at 00:00 a reconcile job syncs Bond SIP instalments whose SIP
 *     date has arrived into the bond table (status In Progress / SIP Investment);
 *   - every day at 00:01 the SIP date itself executes (BONDBP_V8 #1);
 *   - after every application restart the same sync runs in the background, so
 *     a downtime gap is repaired as soon as the app comes back up.
 */
@Component
@EnableScheduling
public class BondSipScheduler {

    private static final Logger log = LoggerFactory.getLogger(BondSipScheduler.class);

    private final BondSipService bondSipService;

    public BondSipScheduler(BondSipService bondSipService) {
        this.bondSipService = bondSipService;
    }

    /** BONDBP_V9 #5: nightly reconcile job at 00:00, server time. */
    @Scheduled(cron = "0 0 0 * * *")
    public void nightlyReconcile() {
        sync("nightly reconcile job");
    }

    /** BONDBP_V8 #1: daily execution at 00:01, server time - the SIP still executes ON its (adjusted) date. */
    @Scheduled(cron = "0 1 0 * * *")
    public void runDaily() {
        sync("daily execution");
    }

    /**
     * BONDBP_V9 #5: same sync right after every application restart. Fires only
     * when a real web server starts (never in MockMvc tests), runs on a
     * background thread so startup is not delayed, and never lets a failure
     * block the application. Ordered after the schema migration listener so the
     * renamed columns are already in place.
     */
    @Order(20)
    @EventListener(ServletWebServerInitializedEvent.class)
    public void syncAfterRestart() {
        Thread.startVirtualThread(() -> sync("restart sync"));
    }

    private void sync(String trigger) {
        try {
            int created = bondSipService.runDueSips();
            log.info("Bond SIP {} finished, {} new bond row(s) created", trigger, created);
        } catch (Exception e) {
            log.error("Bond SIP {} failed", trigger, e);
        }
    }
}
