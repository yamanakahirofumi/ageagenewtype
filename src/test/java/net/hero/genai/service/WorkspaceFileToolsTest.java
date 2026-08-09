package net.hero.genai.service;

import net.hero.genai.model.SecurityRule;
import net.hero.genai.service.SecurityService;
import net.hero.genai.service.WorkspaceFileTools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkspaceFileTools Unit Tests")
public final class WorkspaceFileToolsTest {

    private WorkspaceFileTools fileTools;

    @BeforeEach
    public void setUp() {
        // Arrange
        fileTools = new WorkspaceFileTools();
        SecurityService.getInstance().setEnabled(true);
        SecurityService.getInstance().loadDefaultRules();
        SecurityService.getInstance().clearAuditLogs();
    }

    @Test
    @DisplayName("readFileContent when workspace is not selected should return error message")
    public void readFileContent_WorkspaceNotSelected_ShouldReturnErrorMessage() {
        // Arrange
        SecurityService.getInstance().setActiveWorkspace(null);

        // Act
        final String result = fileTools.readFileContent("dummy.txt");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Error: No active workspace is currently selected"));
    }

    @Test
    @DisplayName("readFileContent when file access permission is denied should return security error")
    public void readFileContent_PermissionDenied_ShouldReturnSecurityError(@TempDir final Path tempDir) throws Exception {
        // Arrange
        final File workspaceDir = tempDir.toFile();
        SecurityService.getInstance().setActiveWorkspace(workspaceDir);

        // Explicitly set a rule that denies access to the specific file
        final List<SecurityRule> denyRules = List.of(
                new SecurityRule("file-access", "${WORKSPACE_DIR}/denied.txt", true)
        );
        SecurityService.getInstance().setRules(denyRules);

        // Create the file first to ensure we test permission block and not file nonexistent check
        final File file = new File(workspaceDir, "denied.txt");
        Files.writeString(file.toPath(), "secret content");

        // Act
        final String result = fileTools.readFileContent("denied.txt");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Security Error: Permission denied"));
    }

    @Test
    @DisplayName("readFileContent when file does not exist should return does not exist error")
    public void readFileContent_FileDoesNotExist_ShouldReturnDoesNotExistError(@TempDir final Path tempDir) {
        // Arrange
        final File workspaceDir = tempDir.toFile();
        SecurityService.getInstance().setActiveWorkspace(workspaceDir);
        // Ensure standard wildcard rule permits file access
        SecurityService.getInstance().setRules(List.of(
                new SecurityRule("file-access", "${WORKSPACE_DIR}/*", false)
        ));

        // Act
        final String result = fileTools.readFileContent("nonexistent.txt");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Error: File does not exist"));
    }

    @Test
    @DisplayName("readFileContent when path is a directory should return directory error")
    public void readFileContent_PathIsDirectory_ShouldReturnDirectoryError(@TempDir final Path tempDir) throws Exception {
        // Arrange
        final File workspaceDir = tempDir.toFile();
        SecurityService.getInstance().setActiveWorkspace(workspaceDir);
        SecurityService.getInstance().setRules(List.of(
                new SecurityRule("file-access", "${WORKSPACE_DIR}/*", false)
        ));

        final File subDir = new File(workspaceDir, "subdir");
        subDir.mkdirs();

        // Act
        final String result = fileTools.readFileContent("subdir");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("is a directory. Use listWorkspaceFiles instead"));
    }

    @Test
    @DisplayName("readFileContent when successful should return content")
    public void readFileContent_Successful_ShouldReturnFileContent(@TempDir final Path tempDir) throws Exception {
        // Arrange
        final File workspaceDir = tempDir.toFile();
        SecurityService.getInstance().setActiveWorkspace(workspaceDir);
        SecurityService.getInstance().setRules(List.of(
                new SecurityRule("file-access", "${WORKSPACE_DIR}/*", false)
        ));

        final File file = new File(workspaceDir, "test.txt");
        final String expectedContent = "Hello from the unit test!";
        Files.writeString(file.toPath(), expectedContent);

        // Act
        final String result = fileTools.readFileContent("test.txt");

        // Assert
        assertEquals(expectedContent, result);
    }

    @Test
    @DisplayName("listWorkspaceFiles when workspace not selected should return error")
    public void listWorkspaceFiles_WorkspaceNotSelected_ShouldReturnErrorMessage() {
        // Arrange
        SecurityService.getInstance().setActiveWorkspace(null);

        // Act
        final String result = fileTools.listWorkspaceFiles(".");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Error: No active workspace is currently selected"));
    }

    @Test
    @DisplayName("listWorkspaceFiles when successful should return structured file list")
    public void listWorkspaceFiles_Successful_ShouldReturnFileList(@TempDir final Path tempDir) throws Exception {
        // Arrange
        final File workspaceDir = tempDir.toFile();
        SecurityService.getInstance().setActiveWorkspace(workspaceDir);
        SecurityService.getInstance().setRules(List.of(
                new SecurityRule("file-access", "${WORKSPACE_DIR}/*", false),
                new SecurityRule("file-access", "${WORKSPACE_DIR}/sub/*", false)
        ));

        final File file1 = new File(workspaceDir, "test1.txt");
        Files.writeString(file1.toPath(), "file 1");

        final File subDir = new File(workspaceDir, "sub");
        subDir.mkdirs();

        final File file2 = new File(subDir, "test2.txt");
        Files.writeString(file2.toPath(), "file 2");

        // Act
        final String result = fileTools.listWorkspaceFiles(".");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("[FILE] test1.txt"));
        assertTrue(result.contains("[DIR]  sub/"));
    }
}
