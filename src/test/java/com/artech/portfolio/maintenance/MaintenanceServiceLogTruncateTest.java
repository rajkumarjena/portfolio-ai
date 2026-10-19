package com.artech.portfolio.maintenance;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BACKUP.md 10: after the admin downloads the application log, the server file
 * must be genuinely ZERO bytes - and logging must carry on from offset 0.
 *
 * Two real production failures are pinned here:
 *
 *  1. The old implementation truncated first and logged its confirmation
 *     afterwards, while open handles still pointed at the old end of the
 *     file. With the server's console output redirected into the same
 *     springboot.log that produced a sparse file: 14,038 bytes apparent size
 *     (ls: 14K / 4K on disk) with a null-byte hole in front - never 0 bytes.
 *
 *  2. RollingFileAppender.stop() also stops its rolling + triggering policy,
 *     and start() refuses to run without them - a naive stop()/start() pair
 *     silently kills all further logging into the file.
 *
 * The test therefore attaches a REAL RollingFileAppender with a
 * SizeAndTimeBasedRollingPolicy, exactly like Spring Boot's default
 * logging.file.name setup.
 */
class MaintenanceServiceLogTruncateTest {

    private static Path logFile;
    private static RollingFileAppender<ILoggingEvent> fileAppender;
    private static MaintenanceService service;

    @BeforeAll
    static void attachARealRollingAppenderToATempLog() throws Exception {
        logFile = Files.createTempFile("maintenance-truncate-", ".log");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d %-5level [%thread] %logger - %msg%n");
        encoder.start();

        SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy =
                new SizeAndTimeBasedRollingPolicy<>();
        policy.setContext(context);
        policy.setFileNamePattern(logFile + ".%d{yyyy-MM-dd}.%i.log");
        policy.setMaxFileSize(FileSize.valueOf("10MB"));
        policy.setMaxHistory(3);

        fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setFile(logFile.toString());
        fileAppender.setAppend(true);
        fileAppender.setEncoder(encoder);
        fileAppender.setRollingPolicy(policy);
        policy.setParent(fileAppender);
        policy.start();
        fileAppender.setTriggeringPolicy(policy);
        fileAppender.start();

        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(fileAppender);

        // Only used by paths this test never calls - truncateLog touches no database.
        service = new MaintenanceService(null,
                "jdbc:sqlite:" + logFile.resolveSibling("unused.db"),
                logFile.toString(), 0);
    }

    @AfterAll
    static void detachAppenderAndDeleteTempLog() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(fileAppender);
        fileAppender.stop();
        Files.deleteIfExists(logFile);
    }

    @Test
    void logIsZeroedAfterDownloadAndLoggingResumesFromOffsetZero() throws Exception {
        // pretend the application has already logged a few kilobytes
        Files.writeString(logFile, "old log content\n".repeat(500),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        service.truncateLog();

        assertEquals(0, Files.size(logFile),
                "BACKUP.md 10 - the log file must be zero bytes after the download");
        assertTrue(fileAppender.isStarted(),
                "the rolling appender must survive the clear - rollingFileAppender.stop() "
                        + "stops its policies and start() refuses to run without them, "
                        + "which would silently kill all further logging");

        // the very next line must land at byte 0
        LoggerFactory.getLogger("truncate-test").info("first line after the clear");

        byte[] content = Files.readAllBytes(logFile);
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.length > 0, "logback must resume logging after the clear");
        assertTrue(text.contains("first line after the clear"),
                "the next line must be readable, not swallowed behind a stale handle");
        assertTrue(text.indexOf('\0') < 0,
                "no null-byte hole: the file must grow from offset 0, not from the "
                        + "old end-of-file position of a stale handle (server saw 14K "
                        + "apparent / 4K on disk instead of 0 bytes)");
        assertTrue(content.length < 512,
                "only the fresh line is expected, not a hole of null bytes plus the line");

        // the just-truncated log must not be archived away by the restarted policy
        try (var siblings = Files.list(logFile.getParent())) {
            long archives = siblings
                    .filter(p -> p.getFileName().toString()
                            .startsWith(logFile.getFileName().toString() + "."))
                    .count();
            assertEquals(0, archives,
                    "restarting the rolling policy must not roll over the cleared log "
                            + "into an empty archive file");
        }
    }
}
