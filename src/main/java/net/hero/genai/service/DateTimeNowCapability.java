package net.hero.genai.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Support AI capability to get current date and time.
 * Argument can optionally specify a DateTimeFormatter pattern (e.g. "yyyy-MM-dd HH:mm:ss").
 */
public final class DateTimeNowCapability implements SupportAICapability {

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Override
    public String getId() {
        return "dateTime-now";
    }

    @Override
    public String execute(final String argument) {
        String pattern = DEFAULT_PATTERN;
        if (argument != null && !argument.isBlank()) {
            pattern = argument.trim();
        }

        try {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.now().format(formatter);
        } catch (Exception e) {
            // Fallback to default pattern if custom pattern fails
            final DateTimeFormatter fallback = DateTimeFormatter.ofPattern(DEFAULT_PATTERN);
            return LocalDateTime.now().format(fallback) + " (Invalid pattern format: " + pattern + ")";
        }
    }
}
