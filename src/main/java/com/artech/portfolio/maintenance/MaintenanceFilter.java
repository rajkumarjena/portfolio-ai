package com.artech.portfolio.maintenance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * BACKUP.md 3 - the request hold.
 *
 * While a backup or refresh is running every NEW request to the database or
 * the application is stopped:
 *   * during DRAINING (the 5-10 second pause) the request the admin just made
 *     and requests already in flight keep running, fresh page views are still
 *     served so every open page can display the warning banner, but every API
 *     call that could start a transaction is refused with 503;
 *   * during ACTIVE (the real file work) everything except the banner-status
 *     poll, static assets and the admin's own maintenance calls is refused.
 *
 * Ordering: Spring Security runs at order -100 (authentication + role checks
 * happen first), this filter runs at -50 and only adds the maintenance hold.
 */
@Component
@Order(-50) // after Spring Security (order -100): roles are known, security already ran
public class MaintenanceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceFilter.class);

    private static final Pattern STATIC_ASSET =
            Pattern.compile(".*\\.(css|js|svg|png|ico|jpg|jpeg|gif|woff2?|ttf|map)$");

    /** Never dangerous, always allowed so the warning can render everywhere. */
    private static final Pattern ALWAYS_ALLOWED = Pattern.compile(
            "^/(api/maintenance/status|api/auth/csrf|logout|error)(/.*)?$");

    private final MaintenanceService maintenance;

    public MaintenanceFilter(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!maintenance.holdActive()) {
            chain.doFilter(request, response);
            return;
        }

        MaintenanceStatus status = maintenance.status();
        String uri = request.getRequestURI();
        boolean get = "GET".equalsIgnoreCase(request.getMethod());

        // The admin's own backup/refresh/log calls must run to finish the job.
        boolean adminMaintenance = isAdmin()
                && uri.startsWith("/api/admin/maintenance");

        if (adminMaintenance
                || ALWAYS_ALLOWED.matcher(uri).matches()
                || STATIC_ASSET.matcher(uri).matches()) {
            chain.doFilter(request, response);
            return;
        }

        // While draining: page views still render (so the banner shows to
        // signed-in AND signed-out users, BACKUP.md 4) - they only read the
        // signed-in user row, they start no transaction. All APIs are held.
        if ("DRAINING".equals(status.phase()) && get
                && (uri.endsWith(".html") || uri.equals("/"))) {
            chain.doFilter(request, response);
            return;
        }

        reject(request, response, status);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        MaintenanceStatus status) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "10");

        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        boolean json = uri.startsWith("/api")
                || (accept != null && accept.contains("application/json"));

        if (json) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"MAINTENANCE\",\"message\":\""
                    + escapeJson(status.message()) + "\"}");
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(errorPage(status.message()));
        }
        log.debug("Held {} {} - database maintenance in progress",
                request.getMethod(), uri);
    }

    private static String escapeJson(String text) {
        return String.valueOf(text)
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static String escapeHtml(String text) {
        return String.valueOf(text)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Small standalone hold page for browsers (BACKUP.md 3). */
    private static String errorPage(String message) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Database Maintenance | Portfolio</title>"
                + "<link rel=\"stylesheet\" href=\"/css/global.css\"></head>"
                + "<body style=\"margin:0;display:flex;min-height:100vh;align-items:center;"
                + "justify-content:center;background:var(--color-bg);padding:16px;\">"
                + "<div class=\"auth-card\" style=\"max-width:480px;text-align:center;\">"
                + "<h2 style=\"color:var(--color-fail);\">Database maintenance in progress</h2>"
                + "<p>" + escapeHtml(message) + "</p>"
                + "<p class=\"muted\">This only takes a few seconds. Please do not start "
                + "new transactions and try again shortly.</p>"
                + "<a class=\"btn btn-primary\" href=\"/\" style=\"justify-content:center;\">"
                + "Try again</a></div></body></html>";
    }
}
