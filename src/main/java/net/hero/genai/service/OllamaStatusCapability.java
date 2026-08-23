package net.hero.genai.service;

import java.util.List;

/**
 * Support AI capability to check Ollama server status and available models.
 * Argument can optionally specify a custom base URL (defaults to http://localhost:11434).
 */
public final class OllamaStatusCapability implements SupportAICapability {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    @Override
    public String getId() {
        return "ollama-status";
    }

    @Override
    public String execute(final String argument) {
        final String baseUrl = (argument != null && !argument.isBlank()) ? argument.trim() : DEFAULT_BASE_URL;
        final OllamaApiService ollamaApiService = new OllamaApiService();
        final boolean online = ollamaApiService.testConnection(baseUrl);
        final List<String> models = ollamaApiService.fetchAvailableModels(baseUrl);

        final StringBuilder sb = new StringBuilder();
        sb.append("Ollama Base URL: ").append(baseUrl).append("\n");
        sb.append("Status: ").append(online ? "ONLINE" : "OFFLINE").append("\n");
        sb.append("Available Models: ").append(String.join(", ", models));
        return sb.toString();
    }
}
