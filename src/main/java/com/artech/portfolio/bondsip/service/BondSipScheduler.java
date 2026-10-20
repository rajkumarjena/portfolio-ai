package com.artech.portfolio.bondsip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BONDBP_V7 #4: automatic Bond SIP execution - every due SIP flips to
 * INPROGRESS on its SIP date (a weekend date rolls to the next weekday) and
 * its Bond SIP Transaction record is created with status INPROGRESS. The same
 * entry point is exposed on /api/bond-sips/run-due so the flow can be
 * triggered manually from the screen (the MF module works the same way).
 */
@Component
@EnableScheduling
public class BondSipScheduler {

    private static final Logger log = LoggerFactory.getLogger(BondSipScheduler.class);

    private final BondSipService bondSipService;

    public BondSipScheduler(BondSipService bondSipService) {
        this.bondSipService = bondSipService;
    }

    /**
     * BONDBP_V8 #1: daily execution at 00:01 AM (server time) to create due
     * Bond SIP transactions; also runs on application start via manual trigger
     * when needed, but @Scheduled handles the daily reboot/time-based run.
     */
    @Scheduled(cron = "0 1 0 * * *")
    public void runDaily() {
        try {
            int run = bondSipService.runDueSips();
            log.info("Bond SIP auto-execution finished, {} SIP(s) executed", run);
        } catch (Exception e) {
            log.error("Bond SIP auto-execution failed", e);
        }
    }
}
