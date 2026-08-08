package net.hero.genai.model;

/**
 * Represents an entry in the security audit log.
 */
public record AuditLogEntry(String timestamp, String category, String operation, String result) {
}
