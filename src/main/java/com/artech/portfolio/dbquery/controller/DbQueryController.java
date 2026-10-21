package com.artech.portfolio.dbquery.controller;

import com.artech.portfolio.auth.service.AuthException;
import com.artech.portfolio.dbquery.dto.DbQueryRequest;
import com.artech.portfolio.dbquery.dto.DbQueryResult;
import com.artech.portfolio.dbquery.service.DbQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ADMIN_V1 Database Query REST API.
 *
 *   POST /api/admin/query   execute one DQL / DML / DDL / TCL statement
 *
 * Role enforcement lives in SecurityConfig (/api/admin/** requires ADMIN) and
 * is re-checked in the service. Validation and database errors come back as a
 * clean {message: ...} body so the screen can show them for a retry.
 */
@RestController
@RequestMapping("/api/admin/query")
public class DbQueryController {

    private final DbQueryService dbQueryService;

    public DbQueryController(DbQueryService dbQueryService) {
        this.dbQueryService = dbQueryService;
    }

    /** ADMIN_V1: validate input, execute the SQL, return the result / error. */
    @PostMapping
    public DbQueryResult execute(@RequestBody DbQueryRequest request) {
        return dbQueryService.execute(request);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("message", ex.getMessage()));
    }
}
