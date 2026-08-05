package net.hero.genai.service;

import net.hero.genai.model.WorkspaceFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkspaceFileService Unit Tests")
public final class WorkspaceFileServiceTest {

    private WorkspaceFileService fileService;

    @BeforeEach
    public void setUp() {
        // Arrange
        fileService = new WorkspaceFileService();
    }

    @Test
    @DisplayName("List files on non-existent directory should return empty list")
    public void listFiles_NonExistentDirectory_ShouldReturnEmptyList() {
        // Act
        final List<WorkspaceFile> files = fileService.listFiles(new File("/nonexistent-dir-123456789"));

        // Assert
        assertNotNull(files);
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Write and read content from a temp file should succeed")
    public void writeFileContent_ValidFile_ShouldSucceedAndBeReadable(@TempDir final Path tempDir) {
        // Arrange
        final File tempFile = tempDir.resolve("testfile.txt").toFile();
        final String expectedContent = "Hello from GenAI Workspace File Service!";

        // Act
        fileService.writeFileContent(tempFile, expectedContent);
        final String actualContent = fileService.readFileContent(tempFile);

        // Assert
        assertEquals(expectedContent, actualContent);
    }

    @Test
    @DisplayName("Reading non-existent file should return empty string")
    public void readFileContent_NonExistentFile_ShouldReturnEmptyString() {
        // Act
        final String content = fileService.readFileContent(new File("/nonexistent-file.txt"));

        // Assert
        assertEquals("", content);
    }

    @Test
    @DisplayName("Writing to directory path as file should throw UncheckedIOException")
    public void writeFileContent_DirectoryPath_ShouldThrowUncheckedIOException(@TempDir final Path tempDir) {
        // Arrange
        final File tempDirFile = tempDir.toFile();

        // Act & Assert
        assertThrows(UncheckedIOException.class, () -> {
            fileService.writeFileContent(tempDirFile, "Some text");
        });
    }
}
