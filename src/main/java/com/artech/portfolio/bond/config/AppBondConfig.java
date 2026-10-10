package com.artech.portfolio.bond.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "bond")
public class AppBondConfig {

    private DateFormat dateFormat = new DateFormat();

    @Setter
    @Getter
    public static class DateFormat {
        private String monthlyInput;
        private String dateInput;
    }
}
