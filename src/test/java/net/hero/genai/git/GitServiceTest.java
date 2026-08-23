package net.hero.genai.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitService Unit Tests")
public final class GitServiceTest {

    private GitService gitService;

    @BeforeEach
    public void setUp() {
        // Arrange
        gitService = new GitService();
    }

    @Test
    @DisplayName("isGitRepository on invalid directory should return false")
    public void isGitRepository_InvalidDirectory_ShouldReturnFalse() {
        // Act
        final boolean isRepo = gitService.isGitRepository(new File("/invalid-directory-path-123"));

        // Assert
        assertFalse(isRepo);
    }

    @Test
    @DisplayName("init should initialize empty git repository and isGitRepository should return true")
    public void init_ValidDirectory_ShouldCreateRepository(@TempDir final Path tempDir) {
        // Arrange
        final File repoDir = tempDir.toFile();

        // Act
        gitService.init(repoDir);
        final boolean isRepo = gitService.isGitRepository(repoDir);

        // Assert
        assertTrue(isRepo);
    }

    @Test
    @DisplayName("getStatus on uninitialized repo should return default status")
    public void getStatus_UninitializedRepo_ShouldReturnNoGitRepoStatus(@TempDir final Path tempDir) {
        // Act
        final GitStatus status = gitService.getStatus(tempDir.toFile());

        // Assert
        assertEquals("No Git Repo", status.branchName());
        assertTrue(status.staged().isEmpty());
        assertTrue(status.unstaged().isEmpty());
        assertTrue(status.untracked().isEmpty());
    }

    @Test
    @DisplayName("stageFile and commit should correctly reflect in git status")
    public void commit_ValidChanges_ShouldUpdateGitStatus(@TempDir final Path tempDir) throws IOException {
        // Arrange
        final File repoDir = tempDir.toFile();
        gitService.init(repoDir);

        final Path newFilePath = tempDir.resolve("README.md");
        Files.writeString(newFilePath, "Hello JGit!");

        // Act - untracked status check
        GitStatus status = gitService.getStatus(repoDir);
        assertTrue(status.untracked().contains("README.md"));

        // Act - stage file
        gitService.stageFile(repoDir, "README.md");
        status = gitService.getStatus(repoDir);
        assertTrue(status.staged().contains("README.md"));
        assertFalse(status.untracked().contains("README.md"));

        // Act - unstage file
        gitService.unstageFile(repoDir, "README.md");
        status = gitService.getStatus(repoDir);
        assertTrue(status.untracked().contains("README.md"));
        assertFalse(status.staged().contains("README.md"));

        // Act - restage and commit
        gitService.stageFile(repoDir, "README.md");
        gitService.commit(repoDir, "Initial commit");
        status = gitService.getStatus(repoDir);

        // Assert
        assertTrue(status.staged().isEmpty());
        assertTrue(status.untracked().isEmpty());
        assertTrue(status.unstaged().isEmpty());
    }

    @Test
    @DisplayName("createBranch and checkout should correctly change the branch name")
    public void checkout_ValidBranch_ShouldChangeActiveBranch(@TempDir final Path tempDir) throws IOException {
        // Arrange
        final File repoDir = tempDir.toFile();
        gitService.init(repoDir);

        // Commit something first to avoid unborn branch issues when branching
        final Path newFilePath = tempDir.resolve("file.txt");
        Files.writeString(newFilePath, "data");
        gitService.stageFile(repoDir, "file.txt");
        gitService.commit(repoDir, "commit 1");

        // Act
        gitService.createBranch(repoDir, "feature-test", false);
        List<String> branches = gitService.listLocalBranches(repoDir);
        assertTrue(branches.contains("feature-test"));

        gitService.checkout(repoDir, "feature-test");
        final GitStatus status = gitService.getStatus(repoDir);

        // Assert
        assertEquals("feature-test", status.branchName());
    }
}
