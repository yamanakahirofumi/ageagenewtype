package net.hero.genai.security;

/**
 * Represents an entry in the security audit log.
 */
public record AuditLogEntry(String timestamp, String category, String operation, String result) {
}
