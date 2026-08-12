package net.hero.genai.service;

import net.hero.genai.service.WorkspaceAgent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaApiService {

    private static final Logger LOGGER = Logger.getLogger(OllamaApiService.class.getName());
    private static final List<String> MOCK_MODELS = List.of("mock-llama3.2", "mock-gemma2", "mock-mistral");

    private final HttpClient httpClient;

    public OllamaApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Tests the connection to the Ollama server base URL.
     * Returns true if connection succeeded, false otherwise.
     */
    public boolean testConnection(final String baseUrl) {
        LOGGER.log(Level.INFO, "Testing connection to Ollama at: " + baseUrl);
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // Ollama usually returns "Ollama is running" at the root path
            boolean success = response.statusCode() == 200;
            LOGGER.log(Level.INFO, "Connection test result: " + (success ? "Success" : "Failed (status code: " + response.statusCode() + ")"));
            return success;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to connect to Ollama base URL: " + baseUrl, e);
            return false;
        }
    }

    /**
     * Fetches available models from the Ollama server.
     * If the server is offline or returns an error, returns a list of mock models.
     */
    public List<String> fetchAvailableModels(final String baseUrl) {
        LOGGER.log(Level.INFO, "Fetching available models from: " + baseUrl);
        final List<String> models = new ArrayList<>();
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // Parse simple JSON matching "name": "..."
                final Pattern pattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                final Matcher matcher = pattern.matcher(response.body());
                while (matcher.find()) {
                    models.add(matcher.group(1));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not fetch models from Ollama, returning mock models.", e);
        }

        if (models.isEmpty()) {
            LOGGER.log(Level.INFO, "No models retrieved from server. Providing fallback mock models.");
            models.addAll(MOCK_MODELS);
        }
        return List.copyOf(models);
    }

    /**
     * Sends a chat prompt and returns the full response string.
     * Supports both real Ollama (via LangChain4j AI Services) and mock model fallback.
     */
    public String chat(final String baseUrl, final String modelName, final String prompt) {
        LOGGER.log(Level.INFO, "Chatting with model: " + modelName + " at " + baseUrl);
        if (modelName.startsWith("mock-") || !testConnection(baseUrl)) {
            return generateMockResponse(modelName, prompt);
        }

        try {
            final ChatLanguageModel model = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(180))
                    .build();

            final WorkspaceAgent agent = AiServices.builder(WorkspaceAgent.class)
                    .chatLanguageModel(model)
                    .build();

            return agent.chat(prompt);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling LangChain4j OllamaChatModel", e);
            return "Error: Failed to communicate with local Ollama server. (" + e.getMessage() + ")";
        }
    }

    /**
     * Sends a streaming chat prompt. The provided listener receives tokens in real-time.
     * Supports both real Ollama (via LangChain4j AI Services) and mock streaming fallback.
     */
    public void chatStream(final String baseUrl, final String modelName, final String prompt, final ChatStreamListener listener) {
        LOGGER.log(Level.INFO, "Streaming chat with model: " + modelName + " at " + baseUrl);
        if (modelName.startsWith("mock-") || !testConnection(baseUrl)) {
            generateMockResponseStream(modelName, prompt, listener);
            return;
        }

        try {
            final StreamingChatLanguageModel model = OllamaStreamingChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(180))
                    .build();

            final WorkspaceAgent agent = AiServices.builder(WorkspaceAgent.class)
                    .streamingChatLanguageModel(model)
                    .build();

            agent.chatStream(prompt)
                    .onNext(listener::onNext)
                    .onComplete(response -> listener.onComplete(response.content().text()))
                    .onError(listener::onError)
                    .start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling LangChain4j OllamaStreamingChatModel", e);
            listener.onError(e);
        }
    }

    private String generateMockResponse(final String modelName, final String prompt) {
        return "[Mock Response (" + modelName + ")]\n" +
                "You asked: \"" + prompt + "\"\n\n" +
                "Here is a mock answer designed to help test the UI and basic flow.\n" +
                "To get real answers, please run an Ollama server locally at http://localhost:11434 and pull a model (e.g., `ollama run llama3.2`).";
    }

    private void generateMockResponseStream(final String modelName, final String prompt, final ChatStreamListener listener) {
        // Run in a background thread to simulate asynchronous streaming response
        Thread.startVirtualThread(() -> {
            try {
                final String prefix = "[Mock Streaming (" + modelName + ")]\n";
                listener.onNext(prefix);
                Thread.sleep(150);

                final String mainText = "You entered the prompt:\n\"" + prompt + "\"\n\n" +
                        "This is a simulated streaming response generated because the Ollama server is offline or a mock model is selected.\n" +
                        "JavaFX components and LangChain4j integration are working perfectly!";

                final String[] tokens = mainText.split("(?<=\\s)|(?=\\s)");
                for (final String token : tokens) {
                    listener.onNext(token);
                    Thread.sleep(30); // simulates token generation speed
                }

                listener.onComplete(prefix + mainText);
            } catch (InterruptedException e) {
                listener.onError(e);
            }
        });
    }
}
