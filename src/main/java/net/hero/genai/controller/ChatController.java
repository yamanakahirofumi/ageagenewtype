package net.hero.genai.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.hero.genai.model.ChatSession;
import net.hero.genai.model.Message;
import net.hero.genai.model.WorkspaceFile;
import net.hero.genai.model.Workflow;
import net.hero.genai.model.WorkflowStepStatus;
import net.hero.genai.service.OllamaApiService;
import net.hero.genai.service.ChatStreamListener;
import net.hero.genai.service.SecurityService;
import net.hero.genai.service.WorkflowService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatController {

    private static final Logger LOGGER = Logger.getLogger(ChatController.class.getName());

    @FXML private ComboBox<String> comboModel;
    @FXML private Button btnRefreshModels;
    @FXML private Button btnOllamaConfig;
    @FXML private VBox configContainer;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesBox;
    @FXML private HBox contextBadge;
    @FXML private Label lblContextName;
    @FXML private TextArea txtPrompt;
    @FXML private Button btnSend;
    @FXML private Label lblSecurityStatus;
    @FXML private HBox securityWarningBanner;

    @FXML private VBox workflowPanel;
    @FXML private Label lblWorkflowTitle;
    @FXML private Label lblWorkflowDesc;
    @FXML private VBox workflowStepsContainer;
    @FXML private HBox workflowProposalActions;
    @FXML private Button btnApproveWorkflow;
    @FXML private Button btnRejectWorkflow;
    @FXML private Button btnCancelWorkflow;

    // Injected child controller for OllamaConfig
    @FXML private OllamaConfigController ollamaConfigController;

    private final OllamaApiService apiService = new OllamaApiService();
    private final ChatSession chatSession = new ChatSession();
    private MainWorkspaceController mainWorkspaceController;
    private WorkspaceFile currentContextFile;

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing ChatController...");

        if (ollamaConfigController != null) {
            ollamaConfigController.setChatController(this);
        }

        // Initialize Security status UI
        updateSecurityStatusUI();
        SecurityService.getInstance().registerOnSecurityStateChanged(() -> {
            updateSecurityStatusUI();
        });
        SecurityService.getInstance().registerOnAutoRestore(() -> {
            appendSystemInfoMessage("安全のため、セキュリティ制限を自動的に再有効化しました。");
        });

        // Setup shortkey Ctrl+Enter for sending prompt
        txtPrompt.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                handleSend();
                event.consume();
            }
        });

        // Initialize comboModel with default models
        comboModel.getItems().addAll("mock-llama3.2", "mock-gemma2", "mock-mistral");
        comboModel.getSelectionModel().select(0);
        chatSession.setSelectedModel(comboModel.getSelectionModel().getSelectedItem());

        // Sync selected model to ChatSession and StatusBar
        comboModel.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                chatSession.setSelectedModel(newVal);
                if (mainWorkspaceController != null) {
                    mainWorkspaceController.updateStatusBarModel(newVal);
                }
            }
        });

        // Ensure scroll pane auto-scrolls to bottom when children are added
        messagesBox.heightProperty().addListener((obs, oldVal, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        // Load models asynchronously on startup
        refreshModels(false);
    }

    public void setMainWorkspaceController(final MainWorkspaceController controller) {
        this.mainWorkspaceController = controller;
        // Also update initial model in status bar
        if (mainWorkspaceController != null) {
            mainWorkspaceController.updateStatusBarModel(comboModel.getSelectionModel().getSelectedItem());
        }
    }

    public void setContextFile(final WorkspaceFile file) {
        this.currentContextFile = file;
        if (file != null) {
            lblContextName.setText(file.getName());
            contextBadge.setVisible(true);
            contextBadge.setManaged(true);
            LOGGER.log(Level.INFO, "Set chat file context: " + file.getName());
        } else {
            handleClearContext();
        }
    }

    @FXML
    public void handleClearContext() {
        this.currentContextFile = null;
        contextBadge.setVisible(false);
        contextBadge.setManaged(false);
        LOGGER.log(Level.INFO, "Cleared chat file context.");
    }

    @FXML
    private void handleToggleConfig() {
        boolean visible = !configContainer.isVisible();
        configContainer.setVisible(visible);
        configContainer.setManaged(visible);
        btnOllamaConfig.setText(visible ? "Close Settings" : "Ollama Settings");
    }

    @FXML
    public void handleRefreshModels() {
        LOGGER.log(Level.INFO, "Refresh models clicked.");
        if (ollamaConfigController != null) {
            final String baseUrl = ollamaConfigController.getConfig().getApiBaseUrl();
            btnRefreshModels.setDisable(true);

            Thread.startVirtualThread(() -> {
                final List<String> models = apiService.fetchAvailableModels(baseUrl);
                final boolean connected = apiService.testConnection(baseUrl);
                Platform.runLater(() -> {
                    comboModel.getItems().setAll(models);
                    if (!models.isEmpty()) {
                        comboModel.getSelectionModel().select(0);
                    }
                    btnRefreshModels.setDisable(false);
                    ollamaConfigController.getConfig().setConnected(connected);
                    refreshModels(connected);
                });
            });
        }
    }

    public void refreshModels(final boolean connected) {
        if (mainWorkspaceController != null) {
            mainWorkspaceController.updateStatusBarOllama(connected);
        }

        if (ollamaConfigController != null) {
            final String baseUrl = ollamaConfigController.getConfig().getApiBaseUrl();
            if (comboModel.getItems().size() <= 3 && connected) {
                // Try to load real models from server
                Thread.startVirtualThread(() -> {
                    final List<String> models = apiService.fetchAvailableModels(baseUrl);
                    Platform.runLater(() -> {
                        if (!models.isEmpty()) {
                            comboModel.getItems().setAll(models);
                            comboModel.getSelectionModel().select(0);
                        }
                    });
                });
            }
        }
    }

    @FXML
    private void handleClearHistory() {
        chatSession.clear();
        messagesBox.getChildren().clear();
        LOGGER.log(Level.INFO, "Cleared chat history.");
    }

    @FXML
    private void handleSend() {
        final String promptText = txtPrompt.getText();
        if (promptText == null || promptText.trim().isEmpty()) {
            return;
        }

        txtPrompt.clear();

        WorkflowService service = WorkflowService.getInstance();
        if (service.getActiveWorkflow() != null) {
            appendSystemInfoMessage("Interaction within workflow: " + promptText);
            int idx = service.getCurrentStepIndex();
            if (idx >= 0) {
                String oldOutput = service.getStepOutputs().get(idx);
                service.getStepOutputs().set(idx, "[User Feedback]: " + promptText + "\n\n" + oldOutput);
                runActiveWorkflowStep();
            }
            return;
        }

        Workflow matched = service.matchWorkflow(promptText);
        if (matched != null) {
            service.startWorkflow(matched);
            appendSystemInfoMessage("Predefined workflow matched: " + matched.name() + ". Starting now...");
            updateWorkflowPanelUI();
            runActiveWorkflowStep();
            return;
        }

        boolean isTaskRequest = promptText.length() > 5 && (
            promptText.contains("create") || promptText.contains("make") || promptText.contains("implement") ||
            promptText.contains("build") || promptText.contains("design") || promptText.contains("refactor") ||
            promptText.contains("修正") || promptText.contains("作成") || promptText.contains("検証") ||
            promptText.contains("機能") || promptText.contains("実装") || promptText.contains("設計") ||
            promptText.contains("コード") || promptText.contains("テスト")
        );

        if (isTaskRequest) {
            final String baseUrl = ollamaConfigController != null ? ollamaConfigController.getConfig().getApiBaseUrl() : "http://localhost:11434";
            final String activeModel = chatSession.getSelectedModel();
            appendSystemInfoMessage("No predefined workflow matched. Analyzing task and generating custom workflow...");

            service.generateDynamicWorkflow(promptText, baseUrl, activeModel, apiService, (wf) -> {
                if (wf != null) {
                    service.setProposedWorkflow(wf);
                    updateWorkflowPanelUI();
                    appendSystemInfoMessage("Custom workflow proposed! Please approve to start executing.");
                } else {
                    Platform.runLater(() -> {
                        appendSystemInfoMessage("Failed to generate dynamic workflow. Proceeding with standard single-turn conversation.");
                        executeStandardChat(promptText);
                    });
                }
            });
            return;
        }

        executeStandardChat(promptText);
    }

    private void executeStandardChat(final String promptText) {
        btnSend.setDisable(true);

        String finalPrompt = promptText;
        if (currentContextFile != null && mainWorkspaceController != null) {
            final String fileContent = mainWorkspaceController.getActiveEditorContent();
            finalPrompt = "[File Context: " + currentContextFile.getName() + "]\n" +
                    "```\n" + fileContent + "\n```\n\n" +
                    "User Question:\n" + promptText;
        }

        final Message userMsg = new Message("user", promptText, LocalDateTime.now());
        chatSession.addMessage(userMsg);
        appendMessageUI(userMsg);

        final VBox aiBubble = createMessageBubbleContainer("assistant");
        final Label header = new Label("AI Assistant (" + chatSession.getSelectedModel() + ")");
        header.getStyleClass().add("message-header");
        final Label body = new Label("...");
        body.setWrapText(true);
        body.getStyleClass().add("message-body");
        aiBubble.getChildren().addAll(header, body);
        messagesBox.getChildren().add(aiBubble);

        final String baseUrl = ollamaConfigController != null ? ollamaConfigController.getConfig().getApiBaseUrl() : "http://localhost:11434";
        final String activeModel = chatSession.getSelectedModel();

        final StringBuilder responseBuilder = new StringBuilder();

        apiService.chatStream(baseUrl, activeModel, finalPrompt, new ChatStreamListener() {
            @Override
            public void onNext(String token) {
                Platform.runLater(() -> {
                    if (responseBuilder.length() == 0) {
                        body.setText("");
                    }
                    responseBuilder.append(token);
                    body.setText(responseBuilder.toString());
                });
            }

            @Override
            public void onComplete(String fullResponse) {
                Platform.runLater(() -> {
                    final Message assistantMsg = new Message("assistant", fullResponse, LocalDateTime.now());
                    chatSession.addMessage(assistantMsg);
                    btnSend.setDisable(false);
                });
            }

            @Override
            public void onError(Throwable error) {
                Platform.runLater(() -> {
                    body.setText("Error occurred: " + error.getMessage());
                    body.setStyle("-fx-text-fill: #f44336;");
                    final Message errorMsg = new Message("assistant", "Error: " + error.getMessage(), LocalDateTime.now());
                    chatSession.addMessage(errorMsg);
                    btnSend.setDisable(false);
                });
            }
        });
    }

    private void updateWorkflowPanelUI() {
        Platform.runLater(() -> {
            WorkflowService service = WorkflowService.getInstance();
            Workflow active = service.getActiveWorkflow();
            Workflow proposed = service.getProposedWorkflow();

            if (active != null) {
                workflowPanel.setVisible(true);
                workflowPanel.setManaged(true);
                lblWorkflowTitle.setText("Workflow: " + active.name());
                lblWorkflowDesc.setText(active.description());
                btnCancelWorkflow.setVisible(true);
                workflowProposalActions.setVisible(false);
                workflowProposalActions.setManaged(false);

                workflowStepsContainer.getChildren().clear();
                List<WorkflowStepStatus> statuses = service.getStepStatuses();
                for (int i = 0; i < active.steps().size(); i++) {
                    net.hero.genai.model.WorkflowStep step = active.steps().get(i);
                    WorkflowStepStatus status = statuses.get(i);

                    String statusChar = "⚪";
                    String color = "#858585";
                    if (status == WorkflowStepStatus.RUNNING) {
                        statusChar = "🔵";
                        color = "#007acc";
                    } else if (status == WorkflowStepStatus.SUCCESS) {
                        statusChar = "✅";
                        color = "#2e7d32";
                    } else if (status == WorkflowStepStatus.FAILED) {
                        statusChar = "❌";
                        color = "#f44336";
                    }

                    HBox hbox = new HBox(8);
                    hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    Label lblIcon = new Label(statusChar);
                    lblIcon.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");

                    Label lblStep = new Label("P" + step.phase() + ": " + step.name());
                    lblStep.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");

                    Label lblType = new Label("[" + step.type() + "]");
                    lblType.setStyle("-fx-text-fill: " + ("verify".equals(step.type()) ? "#e0af68" : "#9ece6a") + "; -fx-font-size: 10px;");

                    hbox.getChildren().addAll(lblIcon, lblStep, lblType);
                    workflowStepsContainer.getChildren().add(hbox);
                }
            } else if (proposed != null) {
                workflowPanel.setVisible(true);
                workflowPanel.setManaged(true);
                lblWorkflowTitle.setText("Proposed Workflow: " + proposed.name());
                lblWorkflowDesc.setText(proposed.description());
                btnCancelWorkflow.setVisible(false);
                workflowProposalActions.setVisible(true);
                workflowProposalActions.setManaged(true);

                workflowStepsContainer.getChildren().clear();
                for (int i = 0; i < proposed.steps().size(); i++) {
                    net.hero.genai.model.WorkflowStep step = proposed.steps().get(i);
                    HBox hbox = new HBox(8);
                    hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    Label lblIcon = new Label("⚪");
                    lblIcon.setStyle("-fx-text-fill: #858585;");

                    Label lblStep = new Label("P" + step.phase() + ": " + step.name());
                    lblStep.setStyle("-fx-text-fill: #e0e0e0;");

                    Label lblType = new Label("[" + step.type() + "]");
                    lblType.setStyle("-fx-text-fill: " + ("verify".equals(step.type()) ? "#e0af68" : "#9ece6a") + "; -fx-font-size: 10px;");

                    hbox.getChildren().addAll(lblIcon, lblStep, lblType);
                    workflowStepsContainer.getChildren().add(hbox);
                }
            } else {
                workflowPanel.setVisible(false);
                workflowPanel.setManaged(false);
            }
        });
    }

    @FXML
    public void handleCancelWorkflow() {
        WorkflowService.getInstance().cancelWorkflow();
        updateWorkflowPanelUI();
        appendSystemInfoMessage("Workflow canceled.");
    }

    @FXML
    public void handleApproveWorkflow() {
        WorkflowService service = WorkflowService.getInstance();
        Workflow proposed = service.getProposedWorkflow();
        if (proposed != null) {
            service.startWorkflow(proposed);
            updateWorkflowPanelUI();
            appendSystemInfoMessage("Workflow '" + proposed.name() + "' approved and started!");
            runActiveWorkflowStep();
        }
    }

    @FXML
    public void handleRejectWorkflow() {
        WorkflowService.getInstance().clearProposedWorkflow();
        updateWorkflowPanelUI();
        appendSystemInfoMessage("Proposed workflow rejected.");
    }

    private void runActiveWorkflowStep() {
        WorkflowService service = WorkflowService.getInstance();
        if (service.getActiveWorkflow() == null) {
            return;
        }

        boolean hasNext = service.advanceStep();
        if (!hasNext) {
            appendSystemInfoMessage("Workflow completed successfully!");
            updateWorkflowPanelUI();
            return;
        }

        updateWorkflowPanelUI();

        net.hero.genai.model.WorkflowStep step = service.getActiveWorkflow().steps().get(service.getCurrentStepIndex());

        Platform.runLater(() -> {
            btnSend.setDisable(true);

            final VBox aiBubble = createMessageBubbleContainer("assistant");
            final Label header = new Label("AI Assistant [Workflow: " + step.name() + "]");
            header.getStyleClass().add("message-header");
            final Label body = new Label("Running step...");
            body.setWrapText(true);
            body.getStyleClass().add("message-body");
            aiBubble.getChildren().addAll(header, body);
            messagesBox.getChildren().add(aiBubble);

            final String baseUrl = ollamaConfigController != null ? ollamaConfigController.getConfig().getApiBaseUrl() : "http://localhost:11434";
            final String activeModel = chatSession.getSelectedModel();

            final StringBuilder responseBuilder = new StringBuilder();

            service.executeStep(baseUrl, activeModel, apiService, new ChatStreamListener() {
                @Override
                public void onNext(String token) {
                    Platform.runLater(() -> {
                        if (responseBuilder.length() == 0) {
                            body.setText("");
                        }
                        responseBuilder.append(token);
                        body.setText(responseBuilder.toString());
                    });
                }

                @Override
                public void onComplete(String fullResponse) {
                    Platform.runLater(() -> {
                        final Message assistantMsg = new Message("assistant", fullResponse, LocalDateTime.now());
                        chatSession.addMessage(assistantMsg);
                        btnSend.setDisable(false);
                        updateWorkflowPanelUI();
                        runActiveWorkflowStep();
                    });
                }

                @Override
                public void onError(Throwable error) {
                    Platform.runLater(() -> {
                        body.setText("Error occurred during step: " + error.getMessage());
                        body.setStyle("-fx-text-fill: #f44336;");
                        final Message errorMsg = new Message("assistant", "Error: " + error.getMessage(), LocalDateTime.now());
                        chatSession.addMessage(errorMsg);
                        btnSend.setDisable(false);
                        updateWorkflowPanelUI();
                    });
                }
            }, () -> {
                updateWorkflowPanelUI();
            });
        });
    }

    private void appendMessageUI(final Message message) {
        final VBox bubble = createMessageBubbleContainer(message.role());

        final String displayName = "user".equalsIgnoreCase(message.role()) ? "You" : "AI Assistant";
        final Label header = new Label(displayName + " - " + message.timestamp().toString().substring(11, 19));
        header.getStyleClass().add("message-header");

        final Label body = new Label(message.content());
        body.setWrapText(true);
        body.getStyleClass().add("message-body");

        bubble.getChildren().addAll(header, body);
        messagesBox.getChildren().add(bubble);
    }

    private VBox createMessageBubbleContainer(final String role) {
        final VBox container = new VBox();
        container.getStyleClass().add("chat-message-container");
        if ("user".equalsIgnoreCase(role)) {
            container.getStyleClass().add("user-message");
        } else {
            container.getStyleClass().add("assistant-message");
        }
        return container;
    }

    private void updateSecurityStatusUI() {
        final boolean isEnabled = SecurityService.getInstance().isEnabled();
        if (isEnabled) {
            lblSecurityStatus.setText("🛡️ Security: ON");
            lblSecurityStatus.setStyle("-fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 3px 6px; -fx-background-radius: 3px; -fx-background-color: #2e7d32; -fx-text-fill: #ffffff;");
            securityWarningBanner.setVisible(false);
            securityWarningBanner.setManaged(false);
        } else {
            lblSecurityStatus.setText("⚠️ Security: OFF");
            lblSecurityStatus.setStyle("-fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 3px 6px; -fx-background-radius: 3px; -fx-background-color: #d32f2f; -fx-text-fill: #ffffff;");
            securityWarningBanner.setVisible(true);
            securityWarningBanner.setManaged(true);
        }
    }

    private void appendSystemInfoMessage(final String text) {
        final VBox bubble = new VBox();
        bubble.getStyleClass().addAll("chat-message-container", "assistant-message");
        bubble.setStyle("-fx-background-color: #2b2d30; -fx-border-color: #ff9800; -fx-border-width: 0 0 0 3px;");

        final Label header = new Label("System - Security Notification");
        header.getStyleClass().add("message-header");
        header.setStyle("-fx-text-fill: #ff9800;");

        final Label body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add("message-body");
        body.setStyle("-fx-text-fill: #e0e0e0; -fx-font-style: italic;");

        bubble.getChildren().addAll(header, body);
        messagesBox.getChildren().add(bubble);
    }

    @FXML
    private void handleOpenSecuritySettings() {
        try {
            final javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/net/hero/genai/fxml/SecuritySettings.fxml"));
            final javafx.scene.Parent root = loader.load();
            final javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setTitle("セキュリティマネージャ設定");
            stage.setScene(new javafx.scene.Scene(root, 700, 550));

            // Refresh audit logs when opening
            final SecuritySettingsController controller = loader.getController();
            if (controller != null) {
                controller.refreshAuditLogs();
            }

            stage.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load SecuritySettings.fxml", e);
        }
    }

    @FXML
    private void handleQuickEnableSecurity() {
        SecurityService.getInstance().setEnabled(true);
    }
}
