package com.artech.portfolio.mf.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily auto-execution of due SIP instalments (MF.md 7): every active SIP runs
 * from its start date to its end date with the configured period gap. The same
 * entry point is exposed on /api/mf/sips/run-due so users can trigger it
 * manually from the SIP screen (useful right after entering a backdated SIP).
 */
@Component
@EnableScheduling
public class MfSipScheduler {

    private static final Logger log = LoggerFactory.getLogger(MfSipScheduler.class);

    private final MfService mfService;

    public MfSipScheduler(MfService mfService) {
        this.mfService = mfService;
    }

    /** 03:00 every day, server time. */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        try {
            int run = mfService.runDueSips();
            log.info("MF SIP auto-execution finished, {} instalment(s) executed", run);
        } catch (Exception e) {
            log.error("MF SIP auto-execution failed", e);
        }
    }
}
