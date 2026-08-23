package net.hero.genai.supportai;

import net.hero.genai.git.GitService;
import net.hero.genai.security.SecurityService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupportAiService Unit Tests")
public final class SupportAiServiceTest {

    private SupportAiService supportAiService;

    @BeforeEach
    public void setUp() {
        supportAiService = SupportAiService.getInstance();
    }

    @Test
    @DisplayName("default capabilities should be registered automatically")
    public void getRegisteredCapabilities_OnStartup_ShouldContainDefaultCapabilities() {
        // Act
        final List<SupportAICapability> caps = supportAiService.getRegisteredCapabilities();

        // Assert
        assertNotNull(caps);
        assertTrue(caps.stream().anyMatch(c -> "security-check".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "list-workflows".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "file-lookup".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "git-status".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "git-log".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "file-read".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "directory-list".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "workspace-info".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "ollama-status".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "security-rules-list".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "file-search".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "system-info".equals(c.getId())));
        assertTrue(caps.stream().anyMatch(c -> "dateTime-now".equals(c.getId())));
    }

    @Test
    @DisplayName("registering a custom capability should add it to registry")
    public void registerCapability_CustomCapability_ShouldBeAddedToRegistry() {
        // Arrange
        final SupportAICapability mockCap = new SupportAICapability() {
            @Override
            public String getId() {
                return "mock-ping";
            }

            @Override
            public String execute(String argument) {
                return "PONG:" + argument;
            }
        };

        // Act
        supportAiService.registerCapability(mockCap);
        final String result = supportAiService.invoke("mock-ping", "test");

        // Assert
        assertEquals("PONG:test", result);
        assertNotNull(supportAiService.getCapability("mock-ping"));

        // Clean up
        supportAiService.unregisterCapability("mock-ping");
        assertNull(supportAiService.getCapability("mock-ping"));
    }

    @Test
    @DisplayName("security-check capability should delegate correctly and return permitted/blocked")
    public void invoke_SecurityCheckCapability_ShouldReturnExpectedPermission(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());
        securityService.setEnabled(true);
        securityService.loadDefaultRules();

        try {
            final String secretsPath = tempDir.resolve("docs/secrets/credentials.txt").toAbsolutePath().toString();
            final String manualPath = tempDir.resolve("docs/manual.md").toAbsolutePath().toString();

            final String blockResult = supportAiService.invoke("security-check", "file-access:" + secretsPath);
            assertEquals("BLOCKED", blockResult);

            final String permitResult = supportAiService.invoke("security-check", "file-access:" + manualPath);
            assertEquals("PERMITTED", permitResult);
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("file-lookup capability should verify files in workspace correctly")
    public void invoke_FileLookupCapability_ShouldReturnCorrectStatus(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());

        try {
            final File testFile = tempDir.resolve("test-file.txt").toFile();
            testFile.createNewFile();

            final File testDir = tempDir.resolve("test-subdir").toFile();
            testDir.mkdir();

            assertEquals("EXISTS", supportAiService.invoke("file-lookup", "test-file.txt"));
            assertEquals("DIRECTORY", supportAiService.invoke("file-lookup", "test-subdir"));
            assertEquals("NOT_FOUND", supportAiService.invoke("file-lookup", "non-existent.txt"));
        } catch (Exception e) {
            fail(e);
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("list-workflows capability should return a non-empty explanation of workflows")
    public void invoke_ListWorkflowsCapability_ShouldReturnFormattedString() {
        // Act
        final String result = supportAiService.invoke("list-workflows", null);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("利用可能なワークフローの一覧"));
        assertTrue(result.contains("source-code-creation"));
    }

    @Test
    @DisplayName("git-status capability should return status or not a git repository indicator")
    public void invoke_GitStatusCapability_ShouldReturnGitStatus(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());

        try {
            final String notGitResult = supportAiService.invoke("git-status", null);
            assertEquals("NOT_A_GIT_REPOSITORY", notGitResult);

            final GitService gitService = new GitService();
            gitService.init(tempDir.toFile());

            final String gitResult = supportAiService.invoke("git-status", null);
            assertTrue(gitResult.contains("Branch:"));
            assertTrue(gitResult.contains("Staged:"));
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("git-log capability should return commit history or non-git indicator")
    public void invoke_GitLogCapability_ShouldReturnCommitHistory(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());

        try {
            final String notGitResult = supportAiService.invoke("git-log", "5");
            assertEquals("NOT_A_GIT_REPOSITORY", notGitResult);

            final GitService gitService = new GitService();
            gitService.init(tempDir.toFile());

            final String emptyLogResult = supportAiService.invoke("git-log", "5");
            assertEquals("No commits found.", emptyLogResult);
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("file-read capability should return content of file safely")
    public void invoke_FileReadCapability_ShouldReturnFileContent(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());
        securityService.setEnabled(true);
        securityService.loadDefaultRules();

        try {
            final Path docsDir = tempDir.resolve("docs");
            Files.createDirectories(docsDir);
            final Path sampleFile = docsDir.resolve("sample.txt");
            Files.writeString(sampleFile, "Line 1\nLine 2\nLine 3");

            final String content = supportAiService.invoke("file-read", "docs/sample.txt:2");
            assertTrue(content.contains("Line 1"));
            assertTrue(content.contains("Line 2"));
            assertTrue(content.contains("... [truncated 1 more lines]"));

            final String notFound = supportAiService.invoke("file-read", "docs/missing.txt");
            assertEquals("FILE_NOT_FOUND", notFound);
        } catch (Exception e) {
            fail(e);
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("directory-list capability should list items in workspace directory")
    public void invoke_DirectoryListCapability_ShouldReturnFileList(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());

        try {
            Files.createFile(tempDir.resolve("fileA.txt"));
            Files.createDirectory(tempDir.resolve("dirB"));

            final String listResult = supportAiService.invoke("directory-list", null);
            assertTrue(listResult.contains("[DIR]  dirB/"));
            assertTrue(listResult.contains("[FILE] fileA.txt"));
        } catch (Exception e) {
            fail(e);
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("workspace-info capability should return workspace status metadata")
    public void invoke_WorkspaceInfoCapability_ShouldReturnWorkspaceDetails(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());

        try {
            final String info = supportAiService.invoke("workspace-info", null);
            assertTrue(info.contains("Workspace Active: true"));
            assertTrue(info.contains(tempDir.toFile().getAbsolutePath()));
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("ollama-status capability should return connection status and available models")
    public void invoke_OllamaStatusCapability_ShouldReturnOllamaStatus() {
        // Act
        final String status = supportAiService.invoke("ollama-status", null);

        // Assert
        assertNotNull(status);
        assertTrue(status.contains("Ollama Base URL: http://localhost:11434"));
        assertTrue(status.contains("Status:"));
        assertTrue(status.contains("Available Models:"));
    }

    @Test
    @DisplayName("security-rules-list capability should return active rules list")
    public void invoke_SecurityRulesListCapability_ShouldReturnRulesList() {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        securityService.loadDefaultRules();

        // Act
        final String rulesList = supportAiService.invoke("security-rules-list", null);

        // Assert
        assertNotNull(rulesList);
        assertTrue(rulesList.contains("Security Enforcement:"));
        assertTrue(rulesList.contains("Rules Count:"));
    }

    @Test
    @DisplayName("file-search capability should return matching files")
    public void invoke_FileSearchCapability_ShouldReturnMatchedFiles(@TempDir final Path tempDir) {
        // Arrange
        final SecurityService securityService = SecurityService.getInstance();
        final File prevWorkspace = securityService.getActiveWorkspace();
        securityService.setActiveWorkspace(tempDir.toFile());

        try {
            Files.createFile(tempDir.resolve("AppController.java"));
            Files.createFile(tempDir.resolve("MainView.fxml"));

            final String searchResult = supportAiService.invoke("file-search", "Controller");
            assertTrue(searchResult.contains("Found 1 match(es):"));
            assertTrue(searchResult.contains("AppController.java"));

            final String noMatchResult = supportAiService.invoke("file-search", "NonExistentQuery");
            assertEquals("NO_MATCHES_FOUND", noMatchResult);
        } catch (Exception e) {
            fail(e);
        } finally {
            securityService.setActiveWorkspace(prevWorkspace);
        }
    }

    @Test
    @DisplayName("system-info capability should return OS and Java metadata")
    public void invoke_SystemInfoCapability_ShouldReturnSystemMetadata() {
        // Act
        final String sysInfo = supportAiService.invoke("system-info", null);

        // Assert
        assertNotNull(sysInfo);
        assertTrue(sysInfo.contains("OS:"));
        assertTrue(sysInfo.contains("Java Version:"));
        assertTrue(sysInfo.contains("Memory"));
    }

    @Test
    @DisplayName("dateTime-now capability should return formatted date time string")
    public void invoke_DateTimeNowCapability_ShouldReturnFormattedDateTime() {
        // Act
        final String dateTimeDefault = supportAiService.invoke("dateTime-now", null);
        final String dateTimeCustom = supportAiService.invoke("dateTime-now", "yyyy-MM-dd");

        // Assert
        assertNotNull(dateTimeDefault);
        assertNotNull(dateTimeCustom);
        assertTrue(dateTimeCustom.matches("\\d{4}-\\d{2}-\\d{2}"));
    }
}
