package com.artech.portfolio.maintenance;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.TriggeringPolicy;
import com.artech.portfolio.auth.service.AuthException;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BACKUP.md - the Database Backup and Refresh engine.
 *
 * Lifecycle of a run (points 3-5):
 *   IDLE -> DRAINING  - the button was hit: the banner appears everywhere and
 *                       every NEW request to the application is put on hold
 *                       while the system waits 5-10s so in-flight user
 *                       requests can finish (BACKUP.md 3).
 *   DRAINING -> ACTIVE - the real work starts (snapshot or file replacement).
 *   ACTIVE -> IDLE    - the work is done: the banner disappears by itself
 *                       because every page polls /api/maintenance/status.
 *
 * BACKUP  (points 6/8): a consistent copy of jdbc:sqlite:${user.home}/portfolio.db
 *         is produced with SQLite's VACUUM INTO (safe while the app runs) and
 *         streamed to the admin's browser.
 * REFRESH (point 7):   an uploaded portfolio.db replaces the server file.
 *         Idle Hikari connections are evicted first so no open handle keeps
 *         the old file locked; the previous file is kept as
 *         "portfolio.db.before-refresh" and restored if verification fails.
 * LOG     (points 9/10): streaming the application log and truncating it to
 *         zero bytes after a successful download.
 *
 * No request threads are blocked by this service - holding new requests back
 * is the job of {@link MaintenanceFilter}.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** A run that never reported back (browser closed) resets after 10 minutes. */
    private static final long STALE_MS = 10 * 60 * 1000L;

    /**
     * Watchdog: if the snapshot download never completes (browser killed,
     * download intercepted), the hold is force-released after this long so
     * the application can never stay paused indefinitely. Any real download
     * of a database snapshot finishes well within this.
     */
    private static final long HOLD_MAX_MS = 2 * 60 * 1000L;

    private static final ScheduledExecutorService HOLD_WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "maintenance-hold-watchdog");
                t.setDaemon(true);
                return t;
            });

    /** How long the connection pool gets to fully release the database file. */
    private static final long EVICT_WAIT_MS = 5_000;

    private final DataSource dataSource;
    private final Path dbFile;
    private final Path logFile;
    private final long drainMs;

    private final AtomicReference<MaintenancePhase> phase = new AtomicReference<>(MaintenancePhase.IDLE);
    private volatile MaintenanceProcess process;
    private volatile String message;
    private volatile long updatedAt = System.currentTimeMillis();

    /** Snapshot produced by BACKUP and waiting for its download. */
    private volatile Path snapshot;
    private volatile String snapshotFileName;

    public MaintenanceService(DataSource dataSource,
                              @Value("${spring.datasource.url}") String datasourceUrl,
                              @Value("${logging.file.name:}") String loggingFileName,
                              @Value("${app.maintenance.drain-ms:8000}") long drainMs) {
        this.dataSource = dataSource;
        this.dbFile = parseSqlitePath(datasourceUrl);
        this.logFile = loggingFileName == null || loggingFileName.isBlank()
                ? Path.of(System.getProperty("user.home"), "springboot.log")
                : Path.of(loggingFileName);
        this.drainMs = drainMs;
        log.info("Database maintenance ready - database file: {}, log file: {}, drain pause: {} ms",
                this.dbFile, this.logFile, this.drainMs);
    }

    /** "jdbc:sqlite:/home/u/portfolio.db" -> /home/u/portfolio.db (BACKUP.md 6). */
    private static Path parseSqlitePath(String url) {
        String prefix = "jdbc:sqlite:";
        if (url == null || !url.startsWith(prefix)) {
            throw new IllegalStateException("Unsupported datasource url: " + url);
        }
        String path = url.substring(prefix.length());
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return Path.of(path);
    }

    // ------------------------------------------------------------ state ----

    /** Public banner state; also resets runs that were abandoned >10 minutes. */
    public MaintenanceStatus status() {
        if (phase.get() != MaintenancePhase.IDLE
                && System.currentTimeMillis() - updatedAt > STALE_MS) {
            log.warn("Maintenance {} has been stuck in {} for over 10 minutes - resetting",
                    process, phase.get());
            reset();
        }
        MaintenancePhase current = phase.get();
        if (current == MaintenancePhase.IDLE) {
            return new MaintenanceStatus(false, current.name(), null, null, updatedAt);
        }
        String text = message;
        if (text == null || text.isBlank()) {
            text = MaintenanceStatus.DEFAULT_MESSAGE;
        }
        return new MaintenanceStatus(true, current.name(),
                process == null ? null : process.name(), text, updatedAt);
    }

    /** True while the drain pause or the actual work is running (the filter's gate). */
    public boolean holdActive() {
        if (phase.get() != MaintenancePhase.IDLE
                && System.currentTimeMillis() - updatedAt > STALE_MS) {
            reset();
        }
        return phase.get() != MaintenancePhase.IDLE;
    }

    /** IDLE -> DRAINING; 409 if another run is already in progress. */
    private void begin(MaintenanceProcess p) {
        if (!phase.compareAndSet(MaintenancePhase.IDLE, MaintenancePhase.DRAINING)) {
            throw new AuthException(HttpStatus.CONFLICT,
                    "Another database process (" + process + ") is already running - "
                            + "wait for it to finish and try again.");
        }
        process = p;
        message = p == MaintenanceProcess.BACKUP
                ? "Database backup is about to start - please avoid new transactions "
                        + "for the next few seconds."
                : "Database refresh is about to start - please avoid new transactions "
                        + "for the next few seconds.";
        updatedAt = System.currentTimeMillis();
        log.info("Maintenance {} started - holding new requests, waiting {} ms for "
                + "in-flight requests to finish", p, drainMs);
    }

    private void setActive(MaintenanceProcess p) {
        phase.set(MaintenancePhase.ACTIVE);
        process = p;
        message = p == MaintenanceProcess.BACKUP
                ? "Database backup is in progress - please stay away from new "
                        + "transactions until it finishes."
                : "Database refresh is in progress - please stay away from new "
                        + "transactions until it finishes.";
        updatedAt = System.currentTimeMillis();
        log.info("Maintenance {} drain pause finished - active work begins", p);
    }

    /** ACTIVE/DRAINING -> IDLE; clears the banner and removes the snapshot. */
    private void reset() {
        process = null;
        message = null;
        updatedAt = System.currentTimeMillis();
        phase.set(MaintenancePhase.IDLE);
        cleanupSnapshot();
        log.info("Maintenance back to IDLE - warning banner clears on every open page");
    }

    /** BACKUP.md 3: the 5-10 second pause so pending user requests can finish. */
    private void drainPause() {
        try {
            Thread.sleep(drainMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Maintenance pause was interrupted - the process was cancelled");
        }
    }

    // ----------------------------------------------------------- backup ----

    /** A finished VACUUM INTO snapshot, ready to be streamed to the browser. */
    public record BackupSnapshot(Path path, String fileName) {
    }

    /**
     * BACKUP.md 3/8: drain the application, then produce a consistent copy of
     * the live SQLite file. Runs on the admin's request thread (~8s pause +
     * snapshot); the browser is given the download URL afterwards.
     */
    public BackupSnapshot prepareBackup() {
        begin(MaintenanceProcess.BACKUP);
        boolean ready = false;
        try {
            drainPause();
            setActive(MaintenanceProcess.BACKUP);
            Path target = Files.createTempFile("portfolio-backup-", ".db");
            Files.delete(target); // VACUUM INTO refuses to overwrite
            String sql = "VACUUM INTO '" + target.toString().replace("'", "''") + "'";
            try (Connection c = dataSource.getConnection();
                 Statement s = c.createStatement()) {
                s.execute(sql);
            } catch (SQLException e) {
                throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not create the database snapshot: " + e.getMessage());
            }
            snapshot = target;
            snapshotFileName = "portfolio-backup-" + STAMP.format(LocalDateTime.now()) + ".db";
            log.info("Database snapshot created ({} bytes) for download as {}",
                    Files.size(target), snapshotFileName);
            Path created = target;
            HOLD_WATCHDOG.schedule(() -> {
                if (snapshot == created && phase.get() != MaintenancePhase.IDLE) {
                    log.warn("Backup download did not complete within 2 minutes - "
                            + "releasing the application hold");
                    finishBackup();
                }
            }, HOLD_MAX_MS, TimeUnit.MILLISECONDS);
            ready = true;
            return new BackupSnapshot(target, snapshotFileName);
        } catch (IOException e) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not create the database snapshot: " + e.getMessage());
        } finally {
            if (!ready) {
                reset(); // never leave the app on hold after a failed backup
            }
        }
    }

    public Path snapshotPath() {
        return snapshot;
    }

    /** Download file name of the snapshot waiting to be served. */
    public String snapshotFileName() {
        return snapshotFileName;
    }

    /** Called once the snapshot has been streamed (or failed) - drops the hold. */
    public void finishBackup() {
        log.info("Database snapshot download finished - releasing the application");
        reset();
    }

    private void cleanupSnapshot() {
        Path snap = snapshot;
        snapshot = null;
        snapshotFileName = null;
        if (snap != null) {
            try {
                Files.deleteIfExists(snap);
            } catch (IOException e) {
                log.warn("Could not delete snapshot {}: {}", snap, e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------- refresh ----

    /**
     * BACKUP.md 7: validate the uploaded portfolio.db, drain the application
     * and replace the server's database file with it. The previous file is
     * kept as "<db>.before-refresh" and restored when the new file fails
     * verification.
     */
    public void refreshFrom(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Choose a portfolio.db file from your computer first");
        }
        // Validate BEFORE the banner goes up so a bad file never pauses anyone.
        Path staged = stageAndValidate(file);

        begin(MaintenanceProcess.REFRESH);
        try {
            drainPause();
            setActive(MaintenanceProcess.REFRESH);

            closeIdleConnections();
            Path previous = replaceDatabaseFile(staged);
            try {
                verifyDatabaseFile(dbFile);
            } catch (RuntimeException e) {
                restorePrevious(previous);
                throw e;
            }
            log.info("Database refresh complete - {} is now the live database "
                    + "(previous file kept at {})", dbFile, previous);
        } catch (IOException e) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database refresh failed: " + e.getMessage()
                            + " - the original database was not changed");
        } finally {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException ignored) {
                // leftover staging file in user.home - harmless
            }
            // success or failure: always release the hold (BACKUP.md 5)
            reset();
        }
    }

    /** Copies the upload into user.home (same volume) and checks it is a SQLite portfolio db. */
    private Path stageAndValidate(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "portfolio.db"
                : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".db")) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "The uploaded file must be a .db database file (portfolio.db)");
        }
        Path staged;
        try {
            Path dir = dbFile.getParent() == null ? Path.of(".") : dbFile.getParent();
            staged = Files.createTempFile(dir, "portfolio-upload-", ".db");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the uploaded file: " + e.getMessage());
        }
        try {
            byte[] header = new byte[16];
            try (InputStream in = Files.newInputStream(staged)) {
                if (in.read(header) != header.length
                        || !"SQLite format 3\0".equals(new String(header, 0, 16, java.nio.charset.StandardCharsets.ISO_8859_1))) {
                    throw new AuthException(HttpStatus.BAD_REQUEST,
                            "That file is not a SQLite database - upload the portfolio.db file");
                }
            }
            verifyDatabaseFile(staged);
        } catch (AuthException e) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException ignored) {
            }
            throw e;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException ignored) {
            }
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the uploaded file: " + e.getMessage());
        }
        return staged;
    }

    /**
     * Evicts idle Hikari connections so no open handle keeps portfolio.db
     * locked, then waits (briefly) for in-flight queries to return it.
     */
    private void closeIdleConnections() {
        HikariPoolMXBean pool = null;
        if (dataSource instanceof HikariDataSource hikari) {
            pool = hikari.getHikariPoolMXBean();
        } else {
            try {
                pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
            } catch (SQLException | UnsupportedOperationException ignored) {
                // not a Hikari pool - the move below decides whether it is safe
            }
        }
        if (pool == null) {
            return;
        }
        pool.softEvictConnections();
        long deadline = System.currentTimeMillis() + EVICT_WAIT_MS;
        while (System.currentTimeMillis() < deadline
                && (pool.getIdleConnections() > 0 || pool.getActiveConnections() > 0)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Connection pool evicted before refresh - {} active, {} idle",
                pool.getActiveConnections(), pool.getIdleConnections());
    }

    /**
     * Keeps the old file as .before-refresh, then replaces the database.
     *
     * Windows note: a rename over the live file fails with a sharing violation
     * as long as ANY handle inside the application process still has the file
     * open (sqlite-jdbc handles can briefly outlive their logical close even
     * after the pool reports zero connections). Copying the new bytes into the
     * existing file name needs no delete/rename permission and therefore works;
     * no new connection may touch the file until the hold is released, and
     * {@link #verifyDatabaseFile(Path)} re-opens it fresh afterwards.
     */
    /** How often and how long the file replacement retries while handles release. */
    private static final int REPLACE_ATTEMPTS = 20;
    private static final long REPLACE_RETRY_MS = 500;

    private Path replaceDatabaseFile(Path staged) throws IOException {
        Path previous = null;
        if (Files.exists(dbFile)) {
            previous = dbFile.resolveSibling(dbFile.getFileName() + ".before-refresh");
            Files.copy(dbFile, previous, StandardCopyOption.REPLACE_EXISTING);
        }
        Path wal = dbFile.resolveSibling(dbFile.getFileName() + "-wal");
        Path shm = dbFile.resolveSibling(dbFile.getFileName() + "-shm");
        IOException last = null;
        for (int attempt = 1; attempt <= REPLACE_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(wal);
                Files.deleteIfExists(shm);
                replaceContent(dbFile, staged);
                Files.deleteIfExists(wal);
                Files.deleteIfExists(shm);
                if (attempt > 1) {
                    log.info("Database file replacement succeeded on attempt {}", attempt);
                }
                return previous;
            } catch (IOException e) {
                last = e;
                log.warn("Database file replacement attempt {}/{} failed: {}",
                        attempt, REPLACE_ATTEMPTS, e.getMessage(), e);
                try {
                    Thread.sleep(REPLACE_RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    /**
     * Overwrites the bytes of an existing file in place: truncate + write.
     *
     * Deliberately NOT Files.copy(..., REPLACE_EXISTING) / Files.move - those
     * first DELETE (or rename over) the target, and Windows refuses that while
     * ANY handle in any process still has the file open without delete sharing
     * (sqlite-jdbc connections never share delete). A truncate+write open only
     * needs read/write sharing, which every handle grants - so this works even
     * when a not-yet-released SQLite handle is still around.
     */
    private void replaceContent(Path target, Path source) throws IOException {
        if (!Files.exists(target)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target,
                     StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
    }

    private void restorePrevious(Path previous) {
        if (previous == null) {
            return;
        }
        try {
            replaceContent(dbFile, previous);
            Files.deleteIfExists(dbFile.resolveSibling(dbFile.getFileName() + "-wal"));
            Files.deleteIfExists(dbFile.resolveSibling(dbFile.getFileName() + "-shm"));
            log.warn("Uploaded database failed verification - restored {}", previous);
        } catch (IOException e) {
            log.error("CRITICAL: could not restore {} after a failed refresh", previous, e);
            return;
        }
        try {
            Files.delete(previous); // restore succeeded - drop the safety copy
        } catch (IOException e) {
            log.warn("Could not remove {}: {}", previous, e.getMessage());
        }
    }

    /** Opens the file directly (no pool) and checks it holds a portfolio schema. */
    private void verifyDatabaseFile(Path file) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement s = c.createStatement()) {
            int tables = queryInt(s, "SELECT count(*) FROM sqlite_master WHERE type='table'");
            int client = queryInt(s, "SELECT count(*) FROM sqlite_master WHERE type='table'"
                    + " AND lower(name)='client'");
            if (tables < 5 || client < 1) {
                throw new AuthException(HttpStatus.BAD_REQUEST,
                        "That file is a SQLite database but not a portfolio database "
                                + "(the 'client' table is missing) - upload a portfolio.db "
                                + "file taken from this application");
            }
        } catch (SQLException e) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "The uploaded file could not be opened as a database: " + e.getMessage());
        }
    }

    private int queryInt(Statement s, String sql) throws SQLException {
        try (ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    // -------------------------------------------------------- log download --

    public Path logFilePath() {
        return logFile;
    }

    /**
     * BACKUP.md 10: zero the server log after it has been downloaded.
     *
     * A bare "truncate to 0" is not enough - two open-handle traps make the
     * file come back as a sparse file with a run of null bytes (server
     * observation: 14,038 bytes apparent / 4.0K on disk, "0 bytes" never
     * reached):
     *
     *  1. The confirmation line: logging it AFTER the truncate puts a line
     *     straight back into the file, so it is logged first and wiped with
     *     everything else.
     *  2. Stale offsets: the server runs the app with its console output
     *     redirected into the very same springboot.log
     *     (java -jar app.jar > springboot.log 2>&1). That descriptor keeps
     *     the offset it had BEFORE the truncate, so the next console line is
     *     written past the new end of the file - a hole of null bytes opens
     *     up in front and the file can never read as 0 again. Logback's own
     *     file handle gets the same treatment if logging.file.append=false.
     *
     * So: say it, close logback's file appender (flush + fresh offset on
     * reopen), rewind the stdout descriptor, truncate, rewind once more and
     * verify - retrying if a concurrent line raced the truncate.
     */
    public synchronized void truncateLog() {
        // (1) announce BEFORE zeroing - this line is wiped with the rest
        log.info("Application log downloaded and cleared - {} is now 0 bytes", logFile);

        // (2) close + flush logback's handle(s) writing into this file
        Set<FileAppender<?>> stopped = stopFileAppenders();

        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                // (3) console descriptor back to offset 0 before truncating
                rewindStdout();
                try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.WRITE)) {
                    channel.truncate(0);
                }
                // (4) a line written mid-truncate must also start at byte 0
                rewindStdout();
                if (Files.size(logFile) == 0) {
                    return; // BACKUP.md 10 - genuinely zero bytes
                }
            }
            log.warn("Application log was cleared but {} bytes remain in {}",
                    Files.size(logFile), logFile);
        } catch (IOException e) {
            log.warn("Application log was downloaded but could not be cleared: {}",
                    e.getMessage());
        } finally {
            rewindStdout();
            stopped.forEach(MaintenanceService::startFileAppender);
        }
    }

    /**
     * Starts a stopped file appender again.
     *
     * RollingFileAppender.stop() also stops its rolling + triggering policy,
     * and start() refuses to run while the triggering policy is stopped
     * ("TriggeringPolicy has not started. RollingFileAppender will not
     * start") - which would silently kill all further logging. The policies
     * must therefore be started first; both are LifeCycle children of the
     * appender, so this is logback's own restart order.
     */
    private static void startFileAppender(FileAppender<?> appender) {
        if (appender instanceof RollingFileAppender<?> rolling) {
            RollingPolicy rollingPolicy = rolling.getRollingPolicy();
            TriggeringPolicy<?> triggeringPolicy = rolling.getTriggeringPolicy();
            if (rollingPolicy != null && !rollingPolicy.isStarted()) {
                rollingPolicy.start();
            }
            if (triggeringPolicy != null && !triggeringPolicy.isStarted()) {
                triggeringPolicy.start();
            }
        }
        appender.start();
    }

    /**
     * logback appenders that write into the log file - closed here so the
     * truncate can neither race buffered bytes nor leave logback writing at
     * the old end-of-file offset (relevant when logging.file.append=false).
     */
    private Set<FileAppender<?>> stopFileAppenders() {
        Set<FileAppender<?>> stopped = new LinkedHashSet<>();
        String target = logFile.toAbsolutePath().normalize().toString();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (ch.qos.logback.classic.Logger logger : context.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders(); it.hasNext(); ) {
                Appender<ILoggingEvent> appender = it.next();
                if (appender instanceof FileAppender<?> fileAppender
                        && fileAppender.getFile() != null
                        && Path.of(fileAppender.getFile()).toAbsolutePath().normalize()
                                .toString().equalsIgnoreCase(target)) {
                    fileAppender.stop();
                    stopped.add(fileAppender);
                }
            }
        }
        return stopped;
    }

    /**
     * Rewinds the process stdout to offset 0 (best effort).
     *
     * With `java -jar app.jar > springboot.log 2>&1` the console appender
     * writes through that descriptor into the file being cleared; it holds
     * the pre-truncate offset until something seeks it back, which is exactly
     * what produced the null-byte hole. stderr shares the same descriptor
     * under 2>&1, so one rewind covers both.
     */
    private void rewindStdout() {
        if (!stdoutWritesIntoLogFile()) {
            return;
        }
        try {
            FileChannel stdout = StdoutChannel.CHANNEL;
            if (stdout != null) {
                stdout.position(0);
            }
        } catch (IOException | RuntimeException e) {
            // stdout is a terminal or pipe (seek fails) - nothing to rewind
        }
    }

    /** True when the process stdout was redirected into the log file itself. */
    private boolean stdoutWritesIntoLogFile() {
        try {
            Path fd1 = Path.of("/proc/self/fd/1"); // Linux servers: exact answer
            if (Files.exists(fd1)) {
                return Files.readSymbolicLink(fd1).toAbsolutePath().normalize()
                        .toString()
                        .equalsIgnoreCase(logFile.toAbsolutePath().normalize().toString());
            }
        } catch (IOException | RuntimeException ignored) {
            // not Linux / unreadable - fall through
        }
        // Cannot tell (Windows/macOS dev machines): assume a file redirect; the
        // seek below simply fails on terminals and pipes, so guessing is safe.
        return true;
    }

    /** The process stdout, opened once and kept for the JVM's lifetime. */
    private static final class StdoutChannel {
        /** Never closed on purpose - closing it would close fd 1 (System.out). */
        private static final FileChannel CHANNEL = open();

        private static FileChannel open() {
            try {
                return new FileOutputStream(FileDescriptor.out).getChannel();
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------ helpers ---

    public enum MaintenancePhase {IDLE, DRAINING, ACTIVE}

    public enum MaintenanceProcess {BACKUP, REFRESH}
}
