package com.artech.portfolio.mf.controller;

import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.service.MfImportService;
import com.artech.portfolio.mf.service.MfReconciliationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * KFINTECH report upload and reconciliation endpoints (MF.md 1-3, 13, 16, 17).
 *
 * <p>Every endpoint is scoped to the logged in client; the parsed statement
 * only reaches the permanent tables through accept (MF.md 12/13). Parser
 * failures - a protected file without a password, a wrong password, a file that
 * is not a statement - come back as a 400 carrying the message the screen
 * shows (MF.md 3).</p>
 */
@RestController
@RequestMapping("/api/mf/import")
public class MfImportController {

    private final MfImportService importService;
    private final MfReconciliationService reconciliationService;

    public MfImportController(MfImportService importService,
                              MfReconciliationService reconciliationService) {
        this.importService = importService;
        this.reconciliationService = reconciliationService;
    }

    /** Upload a report and refresh the temporary tables (MF.md 1/9). */
    @PostMapping
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "password", required = false) String password,
                                      Authentication authentication) throws Exception {
        String name = file.getOriginalFilename() == null ? "report.pdf" : file.getOriginalFilename();
        String supplied = password == null || password.isBlank() ? null : password;
        return importService.importFile(me(authentication), name, file.getBytes(), supplied);
    }

    /** Import history of the client (MF.md 17). */
    @GetMapping("/batches")
    public List<MfImportBatch> batches(Authentication authentication) {
        return importService.listBatches(me(authentication));
    }

    /** The parsed statement as JSON for the high level check (MF.md 16). */
    @GetMapping("/batches/{id}/json")
    public ResponseEntity<String> json(@PathVariable Long id, Authentication authentication) {
        String json = importService.parsedJson(id, me(authentication));
        if (json == null || json.isBlank()) {
            throw new RuntimeException("This import has no parsed statement stored");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mf-statement-" + id + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /** The five reconciliation reports of the upload against the accepted data. */
    @GetMapping("/batches/{id}/report")
    public Map<String, Object> report(@PathVariable Long id, Authentication authentication) {
        return reconciliationService.report(id, me(authentication));
    }

    /** Commits the reviewed temporary tables into the permanent ones (MF.md 13). */
    @PostMapping("/batches/{id}/accept")
    public Map<String, Object> accept(@PathVariable Long id, Authentication authentication) {
        return importService.accept(id, me(authentication));
    }

    /** Drops an upload that must not reach the permanent tables (MF.md 17). */
    @DeleteMapping("/batches/{id}")
    public ResponseEntity<Map<String, String>> discard(@PathVariable Long id, Authentication authentication) {
        importService.discard(id, me(authentication));
        return ResponseEntity.ok(Map.of("message", "Import discarded"));
    }

    // ------------------------------------------------------------------ utils

    private static String me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Authentication required");
        }
        return authentication.getName();
    }

    /** MF.md 3 - the parser's own message is what the screen must show. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        String message = ex.getMessage() == null ? "Unable to read the Mutual Fund report" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
