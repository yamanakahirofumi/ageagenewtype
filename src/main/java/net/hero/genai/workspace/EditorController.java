package net.hero.genai.workspace;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

import java.io.File;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EditorController {

    private static final Logger LOGGER = Logger.getLogger(EditorController.class.getName());

    @FXML private TabPane tabPane;

    private final WorkspaceFileService fileService = new WorkspaceFileService();
    private final Map<File, Tab> openTabs = new HashMap<>();
    private MainWorkspaceController mainWorkspaceController;

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing EditorController...");

        // Listen to active tab changes to update line/col status in parent
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateLineColStatus();
        });
    }

    public void setMainWorkspaceController(final MainWorkspaceController controller) {
        this.mainWorkspaceController = controller;
    }

    public void openFile(final WorkspaceFile workspaceFile) {
        if (workspaceFile == null || workspaceFile.isDirectory()) {
            return;
        }

        final File file = workspaceFile.getFile();
        if (openTabs.containsKey(file)) {
            // Tab already open, just select it
            tabPane.getSelectionModel().select(openTabs.get(file));
            return;
        }

        try {
            final String content = fileService.readFileContent(file);
            final Tab tab = new Tab(workspaceFile.getName());
            tab.setUserData(workspaceFile);

            final TextArea textArea = new TextArea(content);
            textArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 14px;");

            // Track cursor position to update line/col status
            textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
                updateLineColStatus();
            });

            tab.setContent(textArea);

            // Clean up tracking when tab is closed
            tab.setOnClosed(e -> {
                openTabs.remove(file);
            });

            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            openTabs.put(file, tab);

            LOGGER.log(Level.INFO, "Opened file in editor: " + file.getName());
        } catch (UncheckedIOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open file: " + file.getName(), e);
        }
    }

    public void saveCurrentFile() {
        final Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            return;
        }

        final WorkspaceFile workspaceFile = (WorkspaceFile) activeTab.getUserData();
        final TextArea textArea = (TextArea) activeTab.getContent();
        if (workspaceFile == null || textArea == null) {
            return;
        }

        try {
            fileService.writeFileContent(workspaceFile.getFile(), textArea.getText());
            LOGGER.log(Level.INFO, "Saved file: " + workspaceFile.getName());
        } catch (UncheckedIOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save file: " + workspaceFile.getName(), e);
        }
    }

    public WorkspaceFile getActiveWorkspaceFile() {
        final Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            return null;
        }
        return (WorkspaceFile) activeTab.getUserData();
    }

    public String getActiveTextOrSelection() {
        final Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            return "";
        }
        final TextArea textArea = (TextArea) activeTab.getContent();
        if (textArea == null) {
            return "";
        }
        final String selectedText = textArea.getSelectedText();
        return selectedText.isEmpty() ? textArea.getText() : selectedText;
    }

    private void updateLineColStatus() {
        if (mainWorkspaceController == null) {
            return;
        }
        final Tab activeTab = tabPane.getSelectionModel().getSelectedItem();
        if (activeTab == null) {
            mainWorkspaceController.updateStatusBarLineCol(1, 1);
            return;
        }
        final TextArea textArea = (TextArea) activeTab.getContent();
        if (textArea == null) {
            mainWorkspaceController.updateStatusBarLineCol(1, 1);
            return;
        }

        final int caretPosition = textArea.getCaretPosition();
        final String text = textArea.getText(0, caretPosition);

        int line = 1;
        int col = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        mainWorkspaceController.updateStatusBarLineCol(line, col);
    }
}
