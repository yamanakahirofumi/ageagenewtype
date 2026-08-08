package net.hero.genai.service;

import net.hero.genai.model.SecurityRule;
import net.hero.genai.model.AuditLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityService Unit Tests")
public final class SecurityServiceTest {

    private SecurityService securityService;

    @BeforeEach
    public void setUp() {
        // Arrange
        securityService = SecurityService.getInstance();
        securityService.setEnabled(true);
        securityService.setAutoRestoreMinutes(10);
        securityService.loadDefaultRules();
        securityService.clearAuditLogs();
    }

    @Test
    @DisplayName("checkPermission with disabled security should always return true")
    public void checkPermission_SecurityDisabled_ShouldAlwaysReturnTrue() {
        // Arrange
        securityService.setEnabled(false);

        // Act
        final boolean result = securityService.checkPermission("file-access", "/any/unpermitted/path", "/workspace");

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("checkPermission on an unpermitted action should trigger default deny")
    public void checkPermission_DefaultDeny_ShouldReturnFalse() {
        // Act
        final boolean result = securityService.checkPermission("file-access", "/etc/passwd", "/workspace");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("checkPermission on a permitted wildcard action should return true")
    public void checkPermission_WildcardMatch_ShouldReturnTrue() {
        // Act
        final boolean result = securityService.checkPermission("http-url", "https://api.github.com/users/jules", null);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("checkPermission on a negated rule match should return false")
    public void checkPermission_NegatedRuleMatched_ShouldReturnFalse() {
        // Act
        final boolean result = securityService.checkPermission("file-access", "/workspace/docs/secrets/credentials.txt", "/workspace");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("save and load configuration from file should match exactly")
    public void saveAndLoadRules_ValidFile_ShouldSucceed(@TempDir final Path tempDir) {
        // Arrange
        final File tempFile = tempDir.resolve("test_security.conf").toFile();
        final List<SecurityRule> originalRules = List.of(
                new SecurityRule("file-access", "${WORKSPACE_DIR}/src/*", false),
                new SecurityRule("program-execution", "!rm", true)
        );
        securityService.setRules(originalRules);

        // Act
        securityService.saveToFile(tempFile);

        // Reset rules to verify loading
        securityService.loadDefaultRules();
        assertNotEquals(originalRules.size(), securityService.getRules().size());

        securityService.loadFromFile(tempFile);
        final List<SecurityRule> reloadedRules = securityService.getRules();

        // Assert
        assertEquals(originalRules.size(), reloadedRules.size());
        assertEquals(originalRules.get(0).pattern(), reloadedRules.get(0).pattern());
        assertEquals(originalRules.get(0).isDeny(), reloadedRules.get(0).isDeny());
        assertEquals(originalRules.get(1).pattern(), reloadedRules.get(1).pattern());
        assertEquals(originalRules.get(1).isDeny(), reloadedRules.get(1).isDeny());
    }

    @Test
    @DisplayName("matchesWildcard helper should correctly identify matches")
    public void matchesWildcard_VariousPatterns_ShouldIdentifyCorrectly() {
        // Act & Assert
        assertTrue(SecurityService.matchesWildcard("/workspace/docs/*", "/workspace/docs/manual.md"));
        assertFalse(SecurityService.matchesWildcard("/workspace/docs/*", "/workspace/src/Main.java"));
        assertTrue(SecurityService.matchesWildcard("https://*.openai.com/*", "https://api.openai.com/v1/chat"));
        assertFalse(SecurityService.matchesWildcard("https://*.openai.com/*", "https://openai.com.malicious.site/"));
    }

    @Test
    @DisplayName("Audit logs should record checks correctly")
    public void checkPermission_AuditLogged_ShouldCreateEntry() {
        // Act
        securityService.checkPermission("file-access", "/workspace/docs/manual.md", "/workspace");
        final List<AuditLogEntry> logs = securityService.getAuditLogs();

        // Assert
        assertFalse(logs.isEmpty());
        final AuditLogEntry latestLog = logs.get(0);
        assertEquals("file-access", latestLog.category());
        assertEquals("/workspace/docs/manual.md", latestLog.operation());
        assertEquals("ALLOW", latestLog.result());
    }
}
