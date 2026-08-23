package net.hero.genai.ollama;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OllamaApiService Unit Tests")
public final class OllamaApiServiceTest {

    private OllamaApiService apiService;

    @BeforeEach
    public void setUp() {
        // Arrange
        apiService = new OllamaApiService();
    }

    @Test
    @DisplayName("Test connection to invalid endpoint should return false")
    public void testConnection_InvalidEndpoint_ShouldReturnFalse() {
        // Act
        final boolean result = apiService.testConnection("http://localhost:9999");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Fetch models on offline server should fallback to mock models")
    public void fetchAvailableModels_OfflineServer_ShouldReturnMockModels() {
        // Act
        final List<String> models = apiService.fetchAvailableModels("http://localhost:9999");

        // Assert
        assertNotNull(models);
        assertFalse(models.isEmpty());
        assertTrue(models.contains("mock-llama3.2"));
        assertTrue(models.contains("mock-mistral"));
    }

    @Test
    @DisplayName("Chat using mock model name should return mock response immediately")
    public void chat_MockModelName_ShouldReturnMockResponse() {
        // Act
        final String response = apiService.chat("http://localhost:11434", "mock-llama3.2", "Hello World");

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("[Mock Response (mock-llama3.2)]"));
        assertTrue(response.contains("Hello World"));
    }

    @Test
    @DisplayName("Chat on invalid offline base URL should fallback to mock response")
    public void chat_OfflineServer_ShouldReturnMockResponse() {
        // Act
        final String response = apiService.chat("http://localhost:9999", "llama3.2", "Hello World");

        // Assert
        assertNotNull(response);
        assertTrue(response.contains("[Mock Response (llama3.2)]"));
    }
}
