package com.artech.portfolio.web;

import com.artech.portfolio.auth.dto.CurrentUserView;
import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.maintenance.MaintenanceService;
import com.artech.portfolio.maintenance.MaintenanceStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * CONSISTENCY.md 5 + BACKUP.md 4/5: every page model gets
 *   "currentUser"  - the signed-in user shown in the top-right corner,
 *   "maintenance"  - the live database-maintenance state so the red warning
 *                    banner is server-rendered on the very first paint (the
 *                    status poll in app.js only keeps it up to date after
 *                    that). One cheap lookup per page view, no per-page
 *                    fetch calls.
 */
@Component
public class CurrentUserInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;
    private final MaintenanceService maintenanceService;

    public CurrentUserInterceptor(CurrentUserService currentUserService,
                                  MaintenanceService maintenanceService) {
        this.currentUserService = currentUserService;
        this.maintenanceService = maintenanceService;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getViewName() == null
                || modelAndView.getViewName().startsWith("redirect:")) {
            return; // REST responses and redirects carry no page model
        }
        CurrentUserView user = currentUserService.view();
        if (user != null) {
            modelAndView.addObject("currentUser", user);
        }
        modelAndView.addObject("maintenance", maintenanceService.status());
    }
}
