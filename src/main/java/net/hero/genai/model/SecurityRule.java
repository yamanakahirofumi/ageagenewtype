package net.hero.genai.model;

/**
 * Represents a security rule for the AI Agent.
 */
public record SecurityRule(String category, String pattern, boolean isDeny) {

    /**
     * Formats this rule as a configuration line.
     *
     * @return the formatted rule string (e.g. "!pattern" or "pattern")
     */
    public String toLine() {
        return (isDeny ? "!" : "") + pattern;
    }
}
