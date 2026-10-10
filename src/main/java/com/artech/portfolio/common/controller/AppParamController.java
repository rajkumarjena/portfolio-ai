package com.artech.portfolio.common.controller;

import com.artech.portfolio.common.entity.AppParamConfig;
import com.artech.portfolio.common.repository.AppParamRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
    private List<AppParamConfig> cachedConfig = new ArrayList<>();

    // Inject refresh rate from application.yaml
    @Value("${app.config.refreshRateDuration}")
    private Duration refreshRateDuration;

    public AppParamController(AppParamRepository appParamRepository) {
        this.appParamRepository = appParamRepository;
    }

    @PostConstruct
    public void loadConfigAtStartup() {
        refreshConfig();
    }

    @PostMapping
    public AppParamConfig saveBond(@RequestBody AppParamConfig appParamConfig) {
        return appParamRepository.save(appParamConfig);
    }

    @Scheduled(fixedRateString = "${app.config.refreshRateDuration}")
    public void refreshConfig() {
        cachedConfig = appParamRepository.findAll();
        System.out.println("Application Param Config refreshed at " + LocalDateTime.now()
                + " | Refresh rate: " + refreshRateDuration);
    }

    public List<AppParamConfig> getConfig() {
        return cachedConfig;
    }
}