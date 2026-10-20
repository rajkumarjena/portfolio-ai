package com.artech.portfolio.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application's single wall clock (BONDBP_V6 #5: widget period ranges are
 * derived from "current date"). Injected as a bean so tests can pin an exact
 * date and assert the week / month / quarter / year boundaries deterministically.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
