package net.hero.genai.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;
import net.hero.genai.model.WorkspaceFile;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainWorkspaceController {

    private static final Logger LOGGER = Logger.getLogger(MainWorkspaceController.class.getName());

    @FXML private MenuItem menuSelectWorkspace;
    @FXML private MenuItem menuClose;
    @FXML private MenuItem menuSave;
    @FXML private SplitPane mainSplitPane;

    @FXML private Label statusOllamaLabel;
    @FXML private Label statusModelLabel;
    @FXML private Label statusLineColLabel;

    // Inject nested controllers
    @FXML private FileTreeController fileTreeController;
    @FXML private EditorController editorController;
    @FXML private ChatController chatViewController;
    @FXML private GitController gitPanelController;

    @FXML private javafx.scene.control.TabPane leftTabPane;
    @FXML private Label statusGitLabel;

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing MainWorkspaceController...");

        // Inject this parent controller reference into nested controllers
        if (fileTreeController != null) {
            fileTreeController.setMainWorkspaceController(this);
        }
        if (editorController != null) {
            editorController.setMainWorkspaceController(this);
        }
        if (chatViewController != null) {
            chatViewController.setMainWorkspaceController(this);
        }
        if (gitPanelController != null) {
            gitPanelController.setMainWorkspaceController(this);
        }
    }

    public void notifyWorkspaceSelected(final java.io.File directory) {
        net.hero.genai.service.SecurityService.getInstance().setActiveWorkspace(directory);
        if (gitPanelController != null) {
            gitPanelController.setWorkspaceDirectory(directory);
        }
    }

    public void refreshFileTree() {
        if (fileTreeController != null && fileTreeController.getWorkspaceDirectory() != null) {
            fileTreeController.loadWorkspace(fileTreeController.getWorkspaceDirectory());
        }
    }

    public void updateGitWidget(final net.hero.genai.model.GitStatus status) {
        javafx.application.Platform.runLater(() -> {
            if ("No Git Repo".equals(status.branchName())) {
                statusGitLabel.setText("⚠️ No Git Repo");
            } else {
                final StringBuilder sb = new StringBuilder("🌿 ");
                sb.append(status.branchName());
                if (status.aheadCount() > 0 || status.behindCount() > 0) {
                    sb.append(" (");
                    if (status.aheadCount() > 0) {
                        sb.append("↑").append(status.aheadCount());
                    }
                    if (status.behindCount() > 0) {
                        if (status.aheadCount() > 0) sb.append(" ");
                        sb.append("↓").append(status.behindCount());
                    }
                    sb.append(")");
                }
                statusGitLabel.setText(sb.toString());
            }
        });
    }

    @FXML
    private void handleGitWidgetClick() {
        if (leftTabPane != null) {
            // Select the second tab (Git Panel)
            leftTabPane.getSelectionModel().select(1);
        }
    }

    public void openFileInEditor(final WorkspaceFile file) {
        if (editorController != null) {
            editorController.openFile(file);
            // Auto sync workspace file context to chat when double clicked / opened
            if (chatViewController != null) {
                chatViewController.setContextFile(file);
            }
        }
    }

    public String getActiveEditorContent() {
        if (editorController != null) {
            return editorController.getActiveTextOrSelection();
        }
        return "";
    }

    @FXML
    private void handleSelectWorkspace() {
        if (fileTreeController != null) {
            fileTreeController.handleSelectWorkspace();
        }
    }

    @FXML
    private void handleSave() {
        if (editorController != null) {
            editorController.saveCurrentFile();
        }
    }

    @FXML
    private void handleClose() {
        LOGGER.log(Level.INFO, "Closing application...");
        final Stage stage = (Stage) mainSplitPane.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleAbout() {
        LOGGER.log(Level.INFO, "Showing About information.");
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("IDE-style GenAI Desktop Application");
        alert.setContentText("A powerful local desktop application integrating JavaFX, LangChain4j, and Ollama.");
        alert.showAndWait();
    }

    // Status bar updates
    public void updateStatusBarOllama(final boolean connected) {
        javafx.application.Platform.runLater(() -> {
            if (connected) {
                statusOllamaLabel.setText("Ollama: Connected");
                statusOllamaLabel.setStyle("-fx-text-fill: #ffffff;");
            } else {
                statusOllamaLabel.setText("Ollama: Offline");
                statusOllamaLabel.setStyle("-fx-text-fill: #e0e0e0;");
            }
        });
    }

    public void updateStatusBarModel(final String model) {
        javafx.application.Platform.runLater(() -> {
            statusModelLabel.setText("Model: " + (model == null ? "None" : model));
        });
    }

    public void updateStatusBarLineCol(final int line, final int col) {
        javafx.application.Platform.runLater(() -> {
            statusLineColLabel.setText("Line " + line + ", Col " + col);
        });
    }
}
