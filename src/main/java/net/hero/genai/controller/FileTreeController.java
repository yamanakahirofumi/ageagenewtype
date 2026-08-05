package net.hero.genai.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.DirectoryChooser;
import net.hero.genai.model.WorkspaceFile;
import net.hero.genai.service.WorkspaceFileService;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FileTreeController {

    private static final Logger LOGGER = Logger.getLogger(FileTreeController.class.getName());

    @FXML private Button btnSelectWorkspace;
    @FXML private Label lblWorkspacePath;
    @FXML private TreeView<WorkspaceFile> treeView;

    private final WorkspaceFileService fileService = new WorkspaceFileService();
    private MainWorkspaceController mainWorkspaceController;

    @FXML
    public void initialize() {
        LOGGER.log(Level.INFO, "Initializing FileTreeController...");

        // Define tree cell factory to display names elegantly and handle click events
        treeView.setCellFactory(tv -> {
            final TreeCell<WorkspaceFile> cell = new TreeCell<>() {
                @Override
                protected void updateItem(WorkspaceFile item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getName());
                    }
                }
            };

            // Double click to open files in editor
            cell.setOnMouseClicked(event -> {
                if (!cell.isEmpty() && event.getClickCount() == 2) {
                    final WorkspaceFile item = cell.getItem();
                    if (item != null && !item.isDirectory() && mainWorkspaceController != null) {
                        mainWorkspaceController.openFileInEditor(item);
                    }
                }
            });

            return cell;
        });
    }

    public void setMainWorkspaceController(final MainWorkspaceController controller) {
        this.mainWorkspaceController = controller;
    }

    @FXML
    public void handleSelectWorkspace() {
        LOGGER.log(Level.INFO, "Select Workspace clicked.");
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Workspace Directory");

        // Set initial directory to current directory if exists
        final File currentDir = new File(".");
        if (currentDir.exists()) {
            directoryChooser.setInitialDirectory(currentDir);
        }

        final File selectedDirectory = directoryChooser.showDialog(btnSelectWorkspace.getScene().getWindow());
        if (selectedDirectory != null) {
            loadWorkspace(selectedDirectory);
        }
    }

    public void loadWorkspace(final File directory) {
        LOGGER.log(Level.INFO, "Loading workspace directory: " + directory.getAbsolutePath());
        lblWorkspacePath.setText(directory.getAbsolutePath());

        final TreeItem<WorkspaceFile> rootItem = buildTreeNodes(new WorkspaceFile(directory));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
    }

    private TreeItem<WorkspaceFile> buildTreeNodes(final WorkspaceFile workspaceFile) {
        final TreeItem<WorkspaceFile> item = new TreeItem<>(workspaceFile);
        if (workspaceFile.isDirectory()) {
            final List<WorkspaceFile> children = fileService.listFiles(workspaceFile.getFile());
            for (final WorkspaceFile child : children) {
                item.getChildren().add(buildTreeNodes(child));
            }
        }
        return item;
    }
}
