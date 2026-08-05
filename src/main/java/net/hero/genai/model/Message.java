package net.hero.genai.model;

import java.time.LocalDateTime;

public record Message(
    String role,        // "user" or "assistant" (or "system")
    String content,
    LocalDateTime timestamp
) {
    public Message {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }
}
