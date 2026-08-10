package net.hero.genai.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
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

import java.io.File;
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
        WorkflowService.getInstance().setStandardChatMode(false);
        WorkflowService.getInstance().clearDeterminationHistory();
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

        // Check if the user is asking about the list of available workflows
        String lowerPrompt = promptText.toLowerCase();
        boolean isAskingForWorkflows = lowerPrompt.contains("どんなワークフロー")
                || lowerPrompt.contains("何のワークフロー")
                || lowerPrompt.contains("ワークフロー一覧")
                || lowerPrompt.contains("ワークフローの一覧")
                || lowerPrompt.contains("利用可能なワークフロー")
                || lowerPrompt.contains("list workflows")
                || lowerPrompt.contains("show workflows");

        if (isAskingForWorkflows) {
            final Message userMsg = new Message("user", promptText, LocalDateTime.now());
            chatSession.addMessage(userMsg);
            appendMessageUI(userMsg);

            StringBuilder response = new StringBuilder();
            response.append("利用可能なワークフローの一覧は以下の通りです：\n\n");
            for (Workflow wf : service.getPredefinedWorkflows()) {
                response.append("■ ").append(wf.name()).append(" (ID: ").append(wf.id()).append(")\n");
                response.append("  概要: ").append(wf.description()).append("\n");
                response.append("  ステップ:\n");
                for (net.hero.genai.model.WorkflowStep step : wf.steps()) {
                    response.append("    フェーズ ").append(step.phase()).append(": ").append(step.name()).append(" [").append(step.type()).append("]\n");
                }
                response.append("\n");
            }

            final Message systemMsg = new Message("assistant", response.toString(), LocalDateTime.now());
            chatSession.addMessage(systemMsg);
            appendMessageUI(systemMsg);
            return;
        }

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

        if (service.isStandardChatMode()) {
            executeStandardChat(promptText);
            return;
        }

        // Run Workflow Determination / Information Gathering Session
        runWorkflowDetermination(promptText);
    }

    private void runWorkflowDetermination(final String promptText) {
        btnSend.setDisable(true);

        final Message userMsg = new Message("user", promptText, LocalDateTime.now());
        chatSession.addMessage(userMsg);
        appendMessageUI(userMsg);

        final VBox aiBubble = createMessageBubbleContainer("assistant");
        final Label header = new Label("AI Assistant (Analyzing Request...)");
        header.getStyleClass().add("message-header");
        final TextArea body = createSelectableTextArea("...", "assistant-message-body");
        aiBubble.getChildren().addAll(header, body);
        messagesBox.getChildren().add(aiBubble);

        final String baseUrl = ollamaConfigController != null ? ollamaConfigController.getConfig().getApiBaseUrl() : "http://localhost:11434";
        final String activeModel = chatSession.getSelectedModel();

        WorkflowService service = WorkflowService.getInstance();
        service.determineWorkflow(promptText, baseUrl, activeModel, apiService, (result) -> {
            Platform.runLater(() -> {
                btnSend.setDisable(false);
                if (result == null) {
                    body.setText("Error determining workflow. Proposing standard chat.");
                    proposeStandardChat(promptText);
                    return;
                }

                // Add to determination history
                service.getDeterminationHistoryWritable().add(userMsg);

                if ("GATHERING".equalsIgnoreCase(result.status())) {
                    body.setText(result.message());
                    final Message assistantMsg = new Message("assistant", result.message(), LocalDateTime.now());
                    chatSession.addMessage(assistantMsg);
                    service.getDeterminationHistoryWritable().add(assistantMsg);
                } else {
                    body.setText(result.message());
                    final Message assistantMsg = new Message("assistant", result.message(), LocalDateTime.now());
                    chatSession.addMessage(assistantMsg);

                    // Build consolidated user request from user's responses in determination history
                    StringBuilder consolidatedRequest = new StringBuilder();
                    consolidatedRequest.append("=== USER REQUEST DETAILS ===\n");
                    for (Message msg : service.getDeterminationHistory()) {
                        if ("user".equalsIgnoreCase(msg.role())) {
                            consolidatedRequest.append("- ").append(msg.content()).append("\n");
                        }
                    }

                    // Handle file access if requested by the support AI and permitted by SecurityService
                    if (result.fileAccessNeeded() && result.fileAccessPath() != null) {
                        File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
                        if (workspaceDir != null) {
                            File file = new File(workspaceDir, result.fileAccessPath());
                            boolean permitted = SecurityService.getInstance().checkPermission("file-access", file.getAbsolutePath(), workspaceDir.getAbsolutePath());
                            if (permitted && file.exists() && file.isFile()) {
                                try {
                                    String fileContent = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                                    consolidatedRequest.append("\n=== FILE CONTEXT ATTACHMENT ===\n");
                                    consolidatedRequest.append("File: ").append(result.fileAccessPath()).append("\n");
                                    consolidatedRequest.append("```\n").append(fileContent).append("\n```\n");
                                    appendSystemInfoMessage("Fetched file context attachment for: " + result.fileAccessPath());
                                } catch (Exception e) {
                                    LOGGER.log(Level.WARNING, "Failed to read file for context attachment: " + file.getAbsolutePath(), e);
                                }
                            } else if (!permitted) {
                                appendSystemInfoMessage("Security Block: Access denied for file context: " + result.fileAccessPath());
                            }
                        }
                    }

                    final String finalUserRequest = consolidatedRequest.toString();

                    service.clearDeterminationHistory();

                    String decision = result.decision();
                    if ("standard".equalsIgnoreCase(decision) || decision == null) {
                        proposeStandardChat(finalUserRequest);
                    } else if ("dynamic".equalsIgnoreCase(decision)) {
                        appendSystemInfoMessage("Decision: Dynamic Workflow. Analyzing and generating steps...");
                        service.generateDynamicWorkflow(finalUserRequest, baseUrl, activeModel, apiService, (wf) -> {
                            if (wf != null) {
                                service.setProposedWorkflow(wf);
                                service.setPendingUserRequest(finalUserRequest);
                                Platform.runLater(() -> {
                                    updateWorkflowPanelUI();
                                    appendSystemInfoMessage("Custom workflow proposed! Please approve to start executing.");
                                });
                            } else {
                                Platform.runLater(() -> {
                                    appendSystemInfoMessage("Failed to generate dynamic workflow. Proposing standard chat instead.");
                                    proposeStandardChat(finalUserRequest);
                                });
                            }
                        });
                    } else {
                        Workflow matched = null;
                        for (Workflow wf : service.getPredefinedWorkflows()) {
                            if (wf.id().equals(decision)) {
                                matched = wf;
                                break;
                            }
                        }
                        if (matched != null) {
                            service.setProposedWorkflow(matched);
                            service.setPendingUserRequest(finalUserRequest);
                            updateWorkflowPanelUI();
                            appendSystemInfoMessage("Predefined workflow '" + matched.name() + "' proposed! Please approve to start executing.");
                        } else {
                            appendSystemInfoMessage("Matched workflow ID '" + decision + "' not found. Proposing standard chat instead.");
                            proposeStandardChat(finalUserRequest);
                        }
                    }
                }
            });
        });
    }

    private void proposeStandardChat(final String finalUserRequest) {
        WorkflowService service = WorkflowService.getInstance();
        Workflow standardChatWf = new Workflow(
            "standard-chat-workflow",
            "標準チャット",
            "ワークフローを使用せずに、通常のAIモデルとやり取りを行う標準チャットを実行します。",
            List.of(),
            List.of(new net.hero.genai.model.WorkflowStep(1, "標準チャットの実行", "output", "通常の対話を行います。", null))
        );
        service.setProposedWorkflow(standardChatWf);
        service.setPendingUserRequest(finalUserRequest);
        updateWorkflowPanelUI();
        appendSystemInfoMessage("Standard chat proposed! Please approve to start conversing.");
    }

    private void executeStandardChatAfterDetermination(final String promptText) {
        btnSend.setDisable(true);

        String finalPrompt = promptText;
        if (currentContextFile != null && mainWorkspaceController != null) {
            final String fileContent = mainWorkspaceController.getActiveEditorContent();
            finalPrompt = "[File Context: " + currentContextFile.getName() + "]\n" +
                    "```\n" + fileContent + "\n```\n\n" +
                    "User Question:\n" + promptText;
        }

        final VBox aiBubble = createMessageBubbleContainer("assistant");
        final Label header = new Label("AI Assistant (" + chatSession.getSelectedModel() + ")");
        header.getStyleClass().add("message-header");
        final TextArea body = createSelectableTextArea("...", "assistant-message-body");
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
        final TextArea body = createSelectableTextArea("...", "assistant-message-body");
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
            String pendingReq = service.getPendingUserRequest();
            if (pendingReq == null || pendingReq.isEmpty()) {
                pendingReq = "Implement user request";
            }
            if ("standard-chat-workflow".equals(proposed.id())) {
                service.setStandardChatMode(true);
                service.clearProposedWorkflow();
                service.clearPendingUserRequest();
                updateWorkflowPanelUI();
                appendSystemInfoMessage("標準チャットを開始します。");
                executeStandardChatAfterDetermination(pendingReq);
            } else {
                service.startWorkflow(proposed, pendingReq);
                service.clearPendingUserRequest();
                updateWorkflowPanelUI();
                appendSystemInfoMessage("Workflow '" + proposed.name() + "' approved and started!");
                runActiveWorkflowStep();
            }
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
            final TextArea body = createSelectableTextArea("Running step...", "assistant-message-body");
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

        final String bodyClass = "user".equalsIgnoreCase(message.role()) ? "user-message-body" : "assistant-message-body";
        final TextArea body = createSelectableTextArea(message.content(), bodyClass);

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

    private TextArea createSelectableTextArea(final String text, final String typeClass) {
        final TextArea textArea = new TextArea(text);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().addAll("selectable-chat-body", typeClass);

        // Create a hidden Text node to measure the text height dynamically
        final Text helper = new Text();
        helper.textProperty().bind(textArea.textProperty());
        helper.fontProperty().bind(textArea.fontProperty());

        // Bind helper's wrapping width to TextArea's width minus some padding.
        // If textArea's width is 0 or very small initially, use a reasonable default.
        helper.wrappingWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            double w = textArea.getWidth();
            return w > 24.0 ? w - 24.0 : 350.0;
        }, textArea.widthProperty()));

        // Bind textArea's prefHeight to the helper's height plus a small buffer.
        textArea.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> {
            double h = helper.getLayoutBounds().getHeight();
            // A small padding to prevent vertical scrollbars and clipping
            return h + 12.0;
        }, helper.layoutBoundsProperty(), textArea.widthProperty()));

        // Set minHeight to prevent any collapse
        textArea.setMinHeight(20.0);

        return textArea;
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

        final TextArea body = createSelectableTextArea(text, "system-message-body");

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
