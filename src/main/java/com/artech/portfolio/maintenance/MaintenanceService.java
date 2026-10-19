package com.artech.portfolio.maintenance;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /** BACKUP.md 10: zero the server log after it has been downloaded. */
    public void truncateLog() {
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                logFile, java.nio.file.StandardOpenOption.WRITE)) {
            ch.truncate(0);
            log.info("Application log downloaded and cleared - {} is now 0 bytes", logFile);
        } catch (IOException e) {
            log.warn("Log was downloaded but could not be cleared: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------ helpers ---

    public enum MaintenancePhase {IDLE, DRAINING, ACTIVE}

    public enum MaintenanceProcess {BACKUP, REFRESH}
}
