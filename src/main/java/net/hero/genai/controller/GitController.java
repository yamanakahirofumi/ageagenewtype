package net.hero.genai.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.hero.genai.model.GitStatus;
import net.hero.genai.service.GitService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GitController {

    private static final Logger LOGGER = Logger.getLogger(GitController.class.getName());

    @FXML private VBox uninitializedBox;
    @FXML private VBox operationsBox;
    @FXML private ComboBox<String> comboBranches;
    @FXML private Button btnNewBranch;
    @FXML private Button btnFetch;
    @FXML private Button btnPull;
    @FXML private Button btnPush;
    @FXML private ListView<ChangeItem> listChanges;
    @FXML private TextArea txtCommitMessage;
    @FXML private Button btnCommit;
    @FXML private Button btnInit;

    @FXML private HBox progressBox;
    @FXML private Label lblProgressMessage;

    private final GitService gitService = new GitService();
    private MainWorkspaceController mainWorkspaceController;
    private File workspaceDirectory;

    // Helper item to represent file status in ListView with checkbox
    public static class ChangeItem {
        private final String path;
        private final String type; // e.g. [Staged], [Unstaged], [Untracked], [Conflict]
        private boolean selected;

        public ChangeItem(String path, String type, boolean selected) {
            this.path = path;
            this.type = type;
            this.selected = selected;
        }

        public String getPath() { return path; }
        public String getType() { return type; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }

        @Override
        public String toString() {
            return type + " " + path;
        }
    }

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing GitController...");

        // Setup custom ListView with Checkbox
        listChanges.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(ChangeItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    checkBox.setText(item.toString());
                    checkBox.setSelected(item.isSelected());
                    checkBox.setOnAction(event -> {
                        item.setSelected(checkBox.isSelected());
                        toggleFileStage(item);
                    });
                    setGraphic(checkBox);
                }
            }
        });

        // Ctrl + Enter on Commit Message Area triggers Commit
        txtCommitMessage.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
                handleCommit();
                event.consume();
            }
        });
    }

    public void setMainWorkspaceController(final MainWorkspaceController controller) {
        this.mainWorkspaceController = controller;
    }

    public void setWorkspaceDirectory(final File directory) {
        this.workspaceDirectory = directory;
        refreshGitState();
    }

    /**
     * Refreshes the Git repository status and updates UI controls.
     */
    public void refreshGitState() {
        if (workspaceDirectory == null) {
            uninitializedBox.setVisible(false);
            uninitializedBox.setManaged(false);
            operationsBox.setVisible(false);
            operationsBox.setManaged(false);
            return;
        }

        final boolean hasGit = gitService.isGitRepository(workspaceDirectory);
        uninitializedBox.setVisible(!hasGit);
        uninitializedBox.setManaged(!hasGit);
        operationsBox.setVisible(hasGit);
        operationsBox.setManaged(hasGit);

        if (hasGit) {
            runGitTask(new Task<GitStatus>() {
                @Override
                protected GitStatus call() {
                    return gitService.getStatus(workspaceDirectory);
                }

                @Override
                protected void succeeded() {
                    final GitStatus status = getValue();
                    updateGitUI(status);
                }

                @Override
                protected void failed() {
                    LOGGER.log(Level.SEVERE, "Failed to update Git state", getException());
                }
            }, "Refreshing Git Status...");
        }
    }

    private void updateGitUI(final GitStatus status) {
        // Prevent triggering listener during programmatic updates
        comboBranches.setOnAction(null);

        // Update branch list and active branch
        final List<String> branches = gitService.listLocalBranches(workspaceDirectory);
        comboBranches.getItems().clear();
        comboBranches.getItems().addAll(branches);
        comboBranches.setValue(status.branchName());

        comboBranches.setOnAction(event -> handleBranchSelection());

        // Update Change Files List
        final List<ChangeItem> items = new ArrayList<>();
        status.staged().forEach(path -> items.add(new ChangeItem(path, "[Staged]", true)));
        status.unstaged().forEach(path -> items.add(new ChangeItem(path, "[Unstaged]", false)));
        status.untracked().forEach(path -> items.add(new ChangeItem(path, "[Untracked]", false)));
        status.conflicting().forEach(path -> items.add(new ChangeItem(path, "[Conflict]", false)));

        listChanges.getItems().clear();
        listChanges.getItems().addAll(items);

        // Notify bottom-left Git Widget
        if (mainWorkspaceController != null) {
            mainWorkspaceController.updateGitWidget(status);
        }
    }

    private void toggleFileStage(final ChangeItem item) {
        final Task<Void> stageTask = new Task<>() {
            @Override
            protected Void call() {
                if (item.isSelected()) {
                    gitService.stageFile(workspaceDirectory, item.getPath());
                } else {
                    gitService.unstageFile(workspaceDirectory, item.getPath());
                }
                return null;
            }

            @Override
            protected void succeeded() {
                refreshGitState();
            }

            @Override
            protected void failed() {
                LOGGER.log(Level.SEVERE, "Failed to toggle staging for file: " + item.getPath(), getException());
                refreshGitState();
            }
        };
        runGitTask(stageTask, item.isSelected() ? "Staging file..." : "Unstaging file...");
    }

    @FXML
    private void handleInit() {
        if (workspaceDirectory == null) return;
        runGitTask(new Task<Void>() {
            @Override
            protected Void call() {
                gitService.init(workspaceDirectory);
                return null;
            }

            @Override
            protected void succeeded() {
                refreshGitState();
            }

            @Override
            protected void failed() {
                showErrorAlert("Init Failed", "Failed to initialize Git repository.");
            }
        }, "Initializing Repository...");
    }

    @FXML
    private void handleBranchSelection() {
        final String selectedBranch = comboBranches.getValue();
        if (selectedBranch == null || workspaceDirectory == null) return;

        runGitTask(new Task<Void>() {
            @Override
            protected Void call() {
                gitService.checkout(workspaceDirectory, selectedBranch);
                return null;
            }

            @Override
            protected void succeeded() {
                refreshGitState();
                if (mainWorkspaceController != null) {
                    mainWorkspaceController.refreshFileTree();
                }
            }

            @Override
            protected void failed() {
                showErrorAlert("Checkout Failed", "Failed to checkout branch " + selectedBranch);
                refreshGitState();
            }
        }, "Checking out " + selectedBranch + "...");
    }

    @FXML
    private void handleNewBranch() {
        if (workspaceDirectory == null) return;

        final TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Branch");
        dialog.setHeaderText("Create a new Git branch");
        dialog.setContentText("Branch Name:");

        final Optional<String> result = dialog.showAndWait();
        result.ifPresent(branchName -> {
            if (branchName.isBlank() || branchName.contains(" ")) {
                showErrorAlert("Invalid Branch Name", "Branch name cannot be empty or contain spaces.");
                return;
            }

            runGitTask(new Task<Void>() {
                @Override
                protected Void call() {
                    gitService.createBranch(workspaceDirectory, branchName, true);
                    return null;
                }

                @Override
                protected void succeeded() {
                    refreshGitState();
                }

                @Override
                protected void failed() {
                    showErrorAlert("Branch Creation Failed", "Could not create branch: " + branchName);
                    refreshGitState();
                }
            }, "Creating branch " + branchName + "...");
        });
    }

    @FXML
    private void handleFetch() {
        if (workspaceDirectory == null) return;
        runGitTask(new Task<Void>() {
            @Override
            protected Void call() {
                gitService.fetch(workspaceDirectory);
                return null;
            }

            @Override
            protected void succeeded() {
                refreshGitState();
                showInfoAlert("Fetch Complete", "Successfully fetched remote changes.");
            }

            @Override
            protected void failed() {
                showErrorAlert("Fetch Failed", "Fetch operation failed.");
                refreshGitState();
            }
        }, "Fetching remote...");
    }

    @FXML
    private void handlePull() {
        if (workspaceDirectory == null) return;
        runGitTask(new Task<Void>() {
            @Override
            protected Void call() {
                gitService.pull(workspaceDirectory);
                return null;
            }

            @Override
            protected void succeeded() {
                refreshGitState();
                if (mainWorkspaceController != null) {
                    mainWorkspaceController.refreshFileTree();
                }
                showInfoAlert("Pull Complete", "Successfully pulled remote changes.");
            }

            @Override
            protected void failed() {
                showErrorAlert("Pull Failed", "Pull operation failed.");
                refreshGitState();
            }
        }, "Pulling changes...");
    }

    @FXML
    private void handlePush() {
        if (workspaceDirectory == null) return;
        runGitTask(new Task<Void>() {
            @Override
            protected Void call() {
                gitService.push(workspaceDirectory);
                return null;
            }

            @Override
            protected void succeeded() {
                refreshGitState();
                showInfoAlert("Push Complete", "Successfully pushed local commits.");
            }

            @Override
            protected void failed() {
                showErrorAlert("Push Failed", "Push operation failed.");
                refreshGitState();
            }
        }, "Pushing changes...");
    }

    @FXML
    private void handleCommit() {
        final String message = txtCommitMessage.getText();
        if (workspaceDirectory == null || message == null || message.isBlank()) {
            showErrorAlert("Empty Message", "Please type a commit message first.");
            return;
        }

        runGitTask(new Task<Void>() {
            @Override
            protected Void call() {
                gitService.commit(workspaceDirectory, message);
                return null;
            }

            @Override
            protected void succeeded() {
                txtCommitMessage.clear();
                refreshGitState();
                showInfoAlert("Commit Successful", "Committed successfully.");
            }

            @Override
            protected void failed() {
                showErrorAlert("Commit Failed", "Failed to commit changes.");
                refreshGitState();
            }
        }, "Committing changes...");
    }

    private void runGitTask(final Task<?> task, final String message) {
        lblProgressMessage.setText(message);
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        operationsBox.setDisable(true);

        // Add action on state transitions in Task (Task implements Worker)
        task.stateProperty().addListener((obs, oldState, newState) -> {
            switch (newState) {
                case SUCCEEDED, FAILED, CANCELLED -> Platform.runLater(() -> {
                    progressBox.setVisible(false);
                    progressBox.setManaged(false);
                    operationsBox.setDisable(false);
                });
                default -> {}
            }
        });

        final Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void showInfoAlert(final String title, final String content) {
        Platform.runLater(() -> {
            final Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showErrorAlert(final String title, final String content) {
        Platform.runLater(() -> {
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
