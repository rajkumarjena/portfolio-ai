package com.artech.portfolio.mfsip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MF.md 7 - the automated side of the Mutual Fund SIP process: every day at
 * 03:00 every due instalment of every running SIP is executed (the SIP list's
 * "Run Due Instalments" button triggers the same, idempotent execution), and
 * the same sync runs right after a restart so a downtime gap is repaired.
 */
@Component
public class MfSipScheduler {

    private static final Logger log = LoggerFactory.getLogger(MfSipScheduler.class);

    private final MfSipService mfSipService;

    public MfSipScheduler(MfSipService mfSipService) {
        this.mfSipService = mfSipService;
    }

    /** Daily execution at 03:00, server time. */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        sync("daily execution");
    }

    /** Same sync after every restart (never in MockMvc tests - no web server). */
    @Order(30)
    @EventListener(ServletWebServerInitializedEvent.class)
    public void syncAfterRestart() {
        Thread.startVirtualThread(() -> sync("restart sync"));
    }

    private void sync(String trigger) {
        try {
            int executed = mfSipService.runDueForAll();
            log.info("Mutual Fund SIP {} finished, {} instalment(s) executed", trigger, executed);
        } catch (Exception e) {
            log.error("Mutual Fund SIP {} failed", trigger, e);
        }
    }
}
