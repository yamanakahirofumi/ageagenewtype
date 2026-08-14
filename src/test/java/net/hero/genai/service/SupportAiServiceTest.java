package net.hero.genai.service;

import net.hero.genai.model.SecurityRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
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
            // Build absolute paths relative to temporary workspace
            final String secretsPath = tempDir.resolve("docs/secrets/credentials.txt").toAbsolutePath().toString();
            final String manualPath = tempDir.resolve("docs/manual.md").toAbsolutePath().toString();

            // Act & Assert
            // docs/secrets/* should be BLOCKED by default
            final String blockResult = supportAiService.invoke("security-check", "file-access:" + secretsPath);
            assertEquals("BLOCKED", blockResult);

            // docs/* (except secrets) should be PERMITTED by default
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
            // Create a test file
            final File testFile = tempDir.resolve("test-file.txt").toFile();
            testFile.createNewFile();

            // Create a test directory
            final File testDir = tempDir.resolve("test-subdir").toFile();
            testDir.mkdir();

            // Act & Assert
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
}
