package net.hero.genai.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import net.hero.genai.model.OllamaConfig;
import net.hero.genai.service.OllamaApiService;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class OllamaConfigController {

    private static final Logger LOGGER = Logger.getLogger(OllamaConfigController.class.getName());

    @FXML private TextField txtApiBaseUrl;
    @FXML private Button btnTestConnection;
    @FXML private Label lblConnectionStatus;

    private final OllamaApiService apiService = new OllamaApiService();
    private final OllamaConfig config = new OllamaConfig();
    private ChatController chatController;

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing OllamaConfigController...");
        // Bind config to text field
        txtApiBaseUrl.setText(config.getApiBaseUrl());
        txtApiBaseUrl.textProperty().addListener((obs, oldVal, newVal) -> {
            config.setApiBaseUrl(newVal);
        });

        updateStatusUI(false);
    }

    public void setChatController(final ChatController chatController) {
        this.chatController = chatController;
    }

    public OllamaConfig getConfig() {
        return this.config;
    }

    @FXML
    private void handleTestConnection() {
        final String baseUrl = config.getApiBaseUrl();
        LOGGER.log(Level.INFO, "Testing connection button clicked. URL: " + baseUrl);

        btnTestConnection.setDisable(true);
        lblConnectionStatus.setText("Status: Testing...");

        // Perform check in a background virtual thread
        Thread.startVirtualThread(() -> {
            final boolean success = apiService.testConnection(baseUrl);
            javafx.application.Platform.runLater(() -> {
                config.setConnected(success);
                updateStatusUI(success);
                btnTestConnection.setDisable(false);

                if (chatController != null) {
                    chatController.refreshModels(success);
                }
            });
        });
    }

    private void updateStatusUI(final boolean connected) {
        if (connected) {
            lblConnectionStatus.setText("Status: Connected");
            lblConnectionStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #4caf50;");
        } else {
            lblConnectionStatus.setText("Status: Offline");
            lblConnectionStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #f44336;");
        }
    }
}
