package com.artech.portfolio.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Central security wiring for AUTH.md:
 *  (a) login screen is the first page of the application
 *  (c) every API and page requires an authenticated user
 *  (d) USER / SUPERVISOR / ADMIN role ladder over all pre-existing APIs
 *  (g) Spring Security + BCrypt + brute-force lockout + CSRF protection
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;

    public SecurityConfig(LoginSuccessHandler loginSuccessHandler,
                          LoginFailureHandler loginFailureHandler) {
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Cookie-based CSRF token so the static-HTML front-ends can read it via JS.
            // The plain (non-XOR) request handler keeps one single token representation:
            // cookie value == form _csrf value == X-XSRF-TOKEN header value.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                // --- public: login/register/reset screens and their APIs ---
                .requestMatchers("/login.html", "/register.html", "/forgot-password.html",
                        "/reset-password.html", "/error",
                        "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password",
                        "/api/auth/validate-token", "/api/auth/csrf",
                        // BACKUP.md 4: every visitor polls the warning-banner state
                        "/api/maintenance/status").permitAll()
                // Public static assets (no data in them): root + one-level subfolders (bonds/, fds/, js/, assets/)
                .requestMatchers("/*.js", "/*.css", "/*.svg", "/*.png", "/*.ico",
                        "/*/*.js", "/*/*.css", "/*/*.svg", "/*/*.png",
                        "/js/**", "/css/**", "/assets/**").permitAll()
                // --- admin business process (AUTH.md e) ---
                .requestMatchers("/admin-users.html", "/admin-db-backup.html",
                        "/admin-db-query.html", "/api/admin/**").hasRole("ADMIN")
                // --- everything else: any authenticated user (USER, SUPERVISOR or ADMIN) ---
                .anyRequest().authenticated())
            // AUTH.md (a): form login on our own static page
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html?logout=1")
                .permitAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new RestAuthenticationEntryPoint()));
        return http.build();
    }

    /** Shared with AuthController so it can issue the first XSRF-TOKEN cookie on demand. */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    /** AUTH.md (h): one-way salted hash - the database never stores a reversible password */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
