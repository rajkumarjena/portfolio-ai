package com.artech.portfolio.common.controller;

import com.artech.portfolio.auth.service.CurrentUserService;
import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.common.repository.AppParamRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/app/config")
public class AppParamController {

    private final AppParamRepository appParamRepository;
    private final CurrentUserService currentUserService;
    private List<AppParamConfig> cachedConfig = new ArrayList<>();

    // Inject refresh rate from application.yaml
    @Value("${app.config.refreshRateDuration}")
    private Duration refreshRateDuration;

    public AppParamController(AppParamRepository appParamRepository,
                              CurrentUserService currentUserService) {
        this.appParamRepository = appParamRepository;
        this.currentUserService = currentUserService;
    }

    @PostConstruct
    public void loadConfigAtStartup() {
        refreshConfig();
    }

    /**
     * ADMIN_V6 #1/#2: the "Application Parameter Configuration" screen fills its
     * Investment Type and Parameter Name drop-downs from THIS cached list - no
     * database query per page load (the scheduled refresh keeps it current).
     */
    @GetMapping
    public List<AppParamConfig> getConfig() {
        return cachedConfig;
    }

    @PostMapping
    public AppParamConfig saveBond(@RequestBody AppParamConfig appParamConfig) {
        // ADMIN_V6 #3/#5: client_id is the signed-in client's id and is never
        // accepted from the request body.
        appParamConfig.setId(null);
        appParamConfig.setClientId(currentUserService.requireClient().getId());
        AppParamConfig saved = appParamRepository.save(appParamConfig);
        // Reload the cache right away so a freshly configured value (e.g. a new
        // bond issuer for the Bond / Bond SIP screens) is served immediately;
        // the scheduled refresh below keeps handling external table changes.
        refreshConfig();
        return saved;
    }

    @Scheduled(fixedRateString = "${app.config.refreshRateDuration}")
    public void refreshConfig() {
        cachedConfig = appParamRepository.findAll();
        System.out.println("Application Param Config refreshed at " + LocalDateTime.now()
                + " | Refresh rate: " + refreshRateDuration);
    }
}