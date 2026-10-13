package com.artech.portfolio.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * AUTH.md (c): every API and page is restricted.
 * Browser page requests are redirected to the login screen (AUTH.md a),
 * while fetch/XHR API calls receive a 401 JSON body so the front-end can react.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String uri = request.getRequestURI();
        boolean apiCall = uri.startsWith("/api/") || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));

        if (apiCall) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"message\":\"Authentication required\",\"path\":\""
                    + uri + "\"}");
        } else {
            String redirect = request.getContextPath() + "/login.html";
            String query = request.getQueryString();
            // Preserve deep links like /bonds/bond-form.html?x=1 so login lands back there
            String requested = request.getRequestURI() + (query != null ? "?" + query : "");
            if (!"/login.html".equals(uri)) {
                redirect += "?next=" + java.net.URLEncoder.encode(requested, java.nio.charset.StandardCharsets.UTF_8);
            }
            response.sendRedirect(redirect);
        }
    }
}
