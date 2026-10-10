package com.artech.portfolio.bond.util;

import com.artech.portfolio.bond.config.AppBondConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Component
public class DateUtils {

    private final DateTimeFormatter monthFormatter;
    private final DateTimeFormatter dateFormatter;

    public DateUtils(AppBondConfig appBondConfig) {
        this.monthFormatter =
                DateTimeFormatter.ofPattern(appBondConfig.getDateFormat().getMonthlyInput());
        this.dateFormatter =
                DateTimeFormatter.ofPattern(appBondConfig.getDateFormat().getDateInput());
    }

    public LocalDate[] convertMonthToBoundaries(String monthStr) {
        YearMonth yearMonth = YearMonth.parse(monthStr.trim(), monthFormatter);
        return new LocalDate[]{yearMonth.atDay(1), yearMonth.atEndOfMonth()};
    }

    public LocalDate parseToLocalDate(String dateStr) {
        String cleanInput = dateStr.trim();
        if (cleanInput.contains("T")) {
            cleanInput = cleanInput.substring(0, 10);
        }
        return LocalDate.parse(cleanInput, dateFormatter);
    }
}
