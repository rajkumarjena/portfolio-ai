package com.artech.portfolio.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Central web wiring for the Thymeleaf front-end (CONSISTENCY.md 1 / 5).
 *
 * Every page URL stays exactly what it was when the pages were plain static
 * HTML, so bookmarks, the login ?next= deep links and the in-app navigation
 * keep working - the pages are now rendered through the shared layout instead
 * of being self-contained files.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserInterceptor currentUserInterceptor;

    public WebConfig(CurrentUserInterceptor currentUserInterceptor) {
        this.currentUserInterceptor = currentUserInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(currentUserInterceptor);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("index");
        registry.addViewController("/index.html").setViewName("index");

        registry.addViewController("/login.html").setViewName("login");
        registry.addViewController("/register.html").setViewName("register");
        registry.addViewController("/forgot-password.html").setViewName("forgot-password");
        registry.addViewController("/reset-password.html").setViewName("reset-password");

        registry.addViewController("/admin-users.html").setViewName("admin-users");
        registry.addViewController("/admin-db-backup.html").setViewName("admin-db-backup");
        registry.addViewController("/admin-db-query.html").setViewName("admin-db-query");

        registry.addViewController("/app-client-config.html").setViewName("app-client-config");
        registry.addViewController("/app-client-view.html").setViewName("app-client-view");
        registry.addViewController("/app-param-config.html").setViewName("app-param-config");

        registry.addViewController("/bonds/bond-form.html").setViewName("bonds/bond-form");
        registry.addViewController("/bonds/bond-view.html").setViewName("bonds/bond-view");
        registry.addViewController("/bonds/bond-return-report.html").setViewName("bonds/bond-return-report");
        registry.addViewController("/bonds/bond-sip.html").setViewName("bonds/bond-sip");

        registry.addViewController("/fds/fd-form.html").setViewName("fds/fd-form");
        registry.addViewController("/fds/fd-view.html").setViewName("fds/fd-view");

        registry.addViewController("/mutualfunds/mf-sip-form.html").setViewName("mutualfunds/mf-sip-form");
        registry.addViewController("/mutualfunds/mf-view.html").setViewName("mutualfunds/mf-view");
        registry.addViewController("/mutualfunds/mf-portfolio.html").setViewName("mutualfunds/mf-portfolio");
        registry.addViewController("/mutualfunds/mf-portfolio-transactions.html")
                .setViewName("mutualfunds/mf-portfolio-transactions");
        registry.addViewController("/mutualfunds/mf-import.html").setViewName("mutualfunds/mf-import");
        registry.addViewController("/mutualfunds/mf-import-view.html").setViewName("mutualfunds/mf-import-view");
    }
}
