package net.hero.genai.service;

import net.hero.genai.model.WorkspaceFile;
import net.hero.genai.service.SecurityService;
import net.hero.genai.service.WorkspaceFileService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorkspaceFileTools {

    private static final Logger LOGGER = Logger.getLogger(WorkspaceFileTools.class.getName());
    private final WorkspaceFileService fileService = new WorkspaceFileService();

    @Tool("Reads the text content of a file in the workspace. The filePath must be relative to the active workspace directory.")
    public String readFileContent(@P("The relative path of the file to read, e.g., 'src/main/java/net/hero/genai/Main.java'") final String filePath) {
        LOGGER.log(Level.INFO, "Tool execution: readFileContent called for " + filePath);

        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "Error: No active workspace is currently selected in the IDE.";
        }

        final File file = new File(workspaceDir, filePath);
        final String absolutePath = file.getAbsolutePath();
        final String workspacePath = workspaceDir.getAbsolutePath();

        final boolean permitted = SecurityService.getInstance().checkPermission("file-access", absolutePath, workspacePath);
        if (!permitted) {
            LOGGER.log(Level.WARNING, "Security check failed for reading file: " + absolutePath);
            return "Security Error: Permission denied by Security Manager to read file: " + filePath;
        }

        if (!file.exists()) {
            return "Error: File does not exist at relative path: " + filePath;
        }
        if (file.isDirectory()) {
            return "Error: '" + filePath + "' is a directory. Use listWorkspaceFiles instead.";
        }

        try {
            return fileService.readFileContent(file);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to read file: " + filePath, e);
            return "Error: Failed to read file content: " + e.getMessage();
        }
    }

    @Tool("Lists files and directories under the specified relative path in the workspace.")
    public String listWorkspaceFiles(@P("The relative directory path to list, use empty string '' or '.' for workspace root") final String relativePath) {
        LOGGER.log(Level.INFO, "Tool execution: listWorkspaceFiles called for " + relativePath);

        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "Error: No active workspace is currently selected in the IDE.";
        }

        final String pathPart = (relativePath == null || relativePath.isBlank()) ? "." : relativePath;
        final File dir = new File(workspaceDir, pathPart);
        final String absolutePath = dir.getAbsolutePath();
        final String workspacePath = workspaceDir.getAbsolutePath();

        final boolean permitted = SecurityService.getInstance().checkPermission("file-access", absolutePath, workspacePath);
        if (!permitted) {
            LOGGER.log(Level.WARNING, "Security check failed for listing directory: " + absolutePath);
            return "Security Error: Permission denied by Security Manager to access: " + relativePath;
        }

        if (!dir.exists()) {
            return "Error: Directory does not exist at relative path: " + relativePath;
        }
        if (!dir.isDirectory()) {
            return "Error: '" + relativePath + "' is a file, not a directory. Use readFileContent to read it.";
        }

        try {
            final List<WorkspaceFile> children = fileService.listFiles(dir);
            if (children.isEmpty()) {
                return "Empty directory: " + relativePath;
            }

            final StringBuilder sb = new StringBuilder();
            sb.append("Files in '").append(relativePath).append("':\n");
            for (final WorkspaceFile child : children) {
                if (child.isDirectory()) {
                    sb.append("[DIR]  ").append(child.getName()).append("/\n");
                } else {
                    sb.append("[FILE] ").append(child.getName()).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list directory contents", e);
            return "Error: Failed to list contents: " + e.getMessage();
        }
    }
}
