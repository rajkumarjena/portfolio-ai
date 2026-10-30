package com.artech.portfolio.mf.controller;

import com.artech.portfolio.mf.service.MfSummaryImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/mf/summary-import")
public class MfSummaryImportController {
    private final MfSummaryImportService service;

    public MfSummaryImportController(MfSummaryImportService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "password", required = false) String password,
                                      Authentication authentication) throws Exception {
        return service.upload(owner(authentication), file.getOriginalFilename() == null ? "summary.pdf" : file.getOriginalFilename(),
                file.getBytes(), password);
    }

    @GetMapping("/batches")
    public java.util.List<com.artech.portfolio.mf.entity.MfImportBatch> batches(Authentication authentication) {
        return service.list(owner(authentication));
    }

    @GetMapping("/batches/{id}/report")
    public Map<String, Object> report(@PathVariable Long id, Authentication authentication) {
        return service.report(id, owner(authentication));
    }

    @DeleteMapping("/batches/{id}")
    public Map<String, Object> discard(@PathVariable Long id, Authentication authentication) {
        service.discard(id, owner(authentication));
        return Map.of("message", "Summary import discarded");
    }

    @PostMapping("/batches/{id}/accept")
    public Map<String, Object> accept(@PathVariable Long id,
                                      @RequestBody(required = false) Map<Long, Map<String, String>> corrections,
                                      Authentication authentication) {
        return service.accept(id, owner(authentication), corrections == null ? Map.of() : corrections);
    }

    private static String owner(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) throw new IllegalArgumentException("Authentication required");
        return authentication.getName();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handle(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message",
                ex.getMessage() == null ? "Unable to process summary statement" : ex.getMessage()));
    }
}
