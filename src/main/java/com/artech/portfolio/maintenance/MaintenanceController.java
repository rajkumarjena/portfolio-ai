package com.artech.portfolio.maintenance;

import com.artech.portfolio.auth.service.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * BACKUP.md - REST endpoints of the admin-only Database Backup and Refresh page.
 *
 *   GET  /api/maintenance/status            public banner state (all users)
 *   POST /api/admin/maintenance/backup      drain + snapshot (BACKUP.md 3/6/8)
 *   GET  /api/admin/maintenance/backup/file stream the snapshot, then release
 *   POST /api/admin/maintenance/refresh     drain + replace the db file (7)
 *   GET  /api/admin/maintenance/log         stream + clear the server log (9/10)
 *
 * Role enforcement lives in SecurityConfig: /api/admin/** requires ROLE_ADMIN,
 * /api/maintenance/status is permitted for everyone (even signed-out visitors).
 */
@RestController
public class MaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceController.class);

    private final MaintenanceService maintenance;

    public MaintenanceController(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    /** Banner polling for every page, signed-in or not (BACKUP.md 4/5). */
    @GetMapping("/api/maintenance/status")
    public ResponseEntity<MaintenanceStatus> status() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(maintenance.status());
    }

    /** BACKUP.md 3/8: hold the app, snapshot portfolio.db, hand back the download URL. */
    @PostMapping("/api/admin/maintenance/backup")
    public Map<String, Object> startBackup() {
        MaintenanceService.BackupSnapshot snapshot = maintenance.prepareBackup();
        log.info("Admin requested database backup - download ready: {}", snapshot.fileName());
        return Map.of(
                "fileName", snapshot.fileName(),
                "downloadUrl", "/api/admin/maintenance/backup/file",
                "message", "Database snapshot is ready - your download starts now.");
    }

    /** Streams the snapshot to the browser, then lifts the hold (whatever happens). */
    @GetMapping("/api/admin/maintenance/backup/file")
    public ResponseEntity<StreamingResponseBody> downloadBackup() throws IOException {
        Path snapshot = maintenance.snapshotPath();
        if (snapshot == null || !Files.exists(snapshot)) {
            throw new AuthException(HttpStatus.GONE,
                    "The backup snapshot is no longer available - start the backup again");
        }
        long size = Files.size(snapshot);
        String fileName = maintenance.snapshotFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "portfolio-backup.db";
        }

        StreamingResponseBody body = out -> {
            try (InputStream in = Files.newInputStream(snapshot)) {
                in.transferTo(out);
            } finally {
                maintenance.finishBackup(); // BACKUP.md 5: banner disappears here
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .body(body);
    }

    /** BACKUP.md 7: upload a local portfolio.db and replace the server's file. */
    @PostMapping("/api/admin/maintenance/refresh")
    public Map<String, Object> refresh(@RequestParam("file") MultipartFile file) {
        maintenance.refreshFrom(file);
        log.info("Admin completed database refresh from upload '{}'",
                file.getOriginalFilename());
        return Map.of(
                "message", "Database refreshed - the uploaded file is now the live database.",
                "fileName", file.getOriginalFilename() == null ? "portfolio.db"
                        : file.getOriginalFilename());
    }

    /** BACKUP.md 9/10: download the server log file and clear it afterwards. */
    @GetMapping("/api/admin/maintenance/log")
    public ResponseEntity<StreamingResponseBody> downloadLog() throws IOException {
        Path logFile = maintenance.logFilePath();
        if (!Files.exists(logFile)) {
            throw new AuthException(HttpStatus.NOT_FOUND,
                    "No application log file exists on the server yet");
        }
        String fileName = logFile.getFileName().toString();
        StreamingResponseBody body = out -> {
            boolean downloaded = false;
            try (InputStream in = Files.newInputStream(logFile)) {
                in.transferTo(out);
                downloaded = true;
            } catch (IOException e) {
                log.warn("Log download aborted, keeping the file: {}", e.getMessage());
            } finally {
                if (downloaded) {
                    maintenance.truncateLog(); // BACKUP.md 10: back to 0 bytes
                }
            }
        };
        // No Content-Length: the app keeps writing to the log while we stream it.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Maintenance request failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Database maintenance failed: " + ex.getMessage()));
    }
}
