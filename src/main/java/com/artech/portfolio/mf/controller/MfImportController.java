package com.artech.portfolio.mf.controller;

import com.artech.portfolio.mf.entity.MfImportBatch;
import com.artech.portfolio.mf.service.MfImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * CAS statement upload + reconciliation endpoints (MF.md 14-16, 20, 22, 23).
 * The batch only reaches MfHeader / MfTransaction through accept().
 */
@RestController
@RequestMapping("/api/mf/import")
public class MfImportController {

    private final MfImportService importService;

    public MfImportController(MfImportService importService) {
        this.importService = importService;
    }

    /** Upload an xlsx / csv / pdf CAS statement (MF.md 14). */
    @PostMapping
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      Authentication authentication) throws Exception {
        String name = file.getOriginalFilename() == null ? "report" : file.getOriginalFilename();
        return importService.importFile(me(authentication), name, file.getBytes());
    }

    @GetMapping("/batches")
    public List<MfImportBatch> batches(Authentication authentication) {
        return importService.listBatches(me(authentication));
    }

    /** Batch + rows + checks + before/after detail report (MF.md 23/25). */
    @GetMapping("/batches/{id}")
    public Map<String, Object> batch(@PathVariable Long id, Authentication authentication) {
        return importService.getBatch(id, me(authentication));
    }

    /** Re-run the header/detail validation and color codes (MF.md 15). */
    @PostMapping("/batches/{id}/validate")
    public Map<String, Object> validate(@PathVariable Long id, Authentication authentication) {
        return importService.validate(id, me(authentication));
    }

    /** Point an unregistered-scheme row at a registered scheme (MF.md 20). */
    @PostMapping("/batches/{id}/remap")
    public Map<String, Object> remap(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     Authentication authentication) {
        Long lineId = asLong(body.get("lineId"), "lineId");
        Long schemeId = asLong(body.get("schemeId"), "schemeId");
        return importService.remap(id, lineId, schemeId, me(authentication));
    }

    /** Commit the reviewed batch into the portfolio (MF.md 16). */
    @PostMapping("/batches/{id}/accept")
    public Map<String, Object> accept(@PathVariable Long id, Authentication authentication) {
        return importService.accept(id, me(authentication));
    }

    @DeleteMapping("/batches/{id}")
    public ResponseEntity<Map<String, String>> discard(@PathVariable Long id,
                                                       Authentication authentication) {
        importService.discard(id, me(authentication));
        return ResponseEntity.ok(Map.of("message", "Batch discarded"));
    }

    // ------------------------------------------------------------------ utils

    private static Long asLong(Object raw, String field) {
        if (raw == null) throw new RuntimeException(field + " is required");
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new RuntimeException(field + " must be a number");
        }
    }

    private static String me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Authentication required");
        }
        return authentication.getName();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
