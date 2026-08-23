package net.hero.genai.supportai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeNowCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "dateTime-now";
    }

    @Override
    public String execute(final String argument) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        if (argument != null && !argument.isBlank()) {
            try {
                DateTimeFormatter.ofPattern(argument.trim());
                pattern = argument.trim();
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }
}
