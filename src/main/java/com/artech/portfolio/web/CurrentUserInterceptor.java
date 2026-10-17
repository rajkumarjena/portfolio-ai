package com.artech.portfolio.web;

import com.artech.portfolio.auth.dto.CurrentUserView;
import com.artech.portfolio.auth.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * CONSISTENCY.md 5: every page shows the signed-in user in the top-right
 * corner. The layout template reads the "currentUser" model attribute that
 * this interceptor injects server-side for all HTML pages - one security
 * lookup per page view, no per-page fetch calls.
 */
@Component
public class CurrentUserInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;

    public CurrentUserInterceptor(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
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
    }
}
