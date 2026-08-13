package net.hero.genai.service;

import javafx.application.Platform;
import net.hero.genai.model.AuditLogEntry;
import net.hero.genai.model.SecurityRule;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing agent security, rules evaluation, configuration, and auto-restoration.
 */
public final class SecurityService {

    private static final Logger LOGGER = Logger.getLogger(SecurityService.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final SecurityService INSTANCE = new SecurityService();

    public static SecurityService getInstance() {
        return INSTANCE;
    }

    private int autoRestoreMinutes = 10; // Default: 10 minutes
    private final List<SecurityRule> rules = new CopyOnWriteArrayList<>();
    private final List<AuditLogEntry> auditLogs = new CopyOnWriteArrayList<>();
    private final List<SecurityChecker> checkers = new CopyOnWriteArrayList<>();
    private File activeWorkspace;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "SecurityRestoreTimer");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledFuture<?> restoreTaskFuture;
    private Runnable onSecurityStateChangedCallback;
    private Runnable onAutoRestoreCallback;

    private SecurityService() {
        // Register default checkers
        checkers.add(new FileAccessSecurityChecker());
        checkers.add(new ProgramExecutionSecurityChecker());
        checkers.add(new HttpUrlSecurityChecker());

        loadDefaultRules();
    }

    /**
     * Registers a custom security checker.
     *
     * @param checker the custom SecurityChecker to register
     */
    public void registerChecker(final SecurityChecker checker) {
        if (checker != null) {
            checkers.add(0, checker); // Add at the beginning to allow custom overrides
            LOGGER.log(Level.INFO, "Registered custom SecurityChecker: " + checker.getClass().getName());
        }
    }

    /**
     * Unregisters a security checker.
     *
     * @param checker the SecurityChecker to unregister
     */
    public void unregisterChecker(final SecurityChecker checker) {
        if (checker != null) {
            checkers.remove(checker);
            LOGGER.log(Level.INFO, "Unregistered SecurityChecker: " + checker.getClass().getName());
        }
    }

    /**
     * Returns a copy of the currently registered checkers.
     *
     * @return an immutable list of registered security checkers
     */
    public List<SecurityChecker> getCheckers() {
        return List.copyOf(checkers);
    }

    /**
     * Initializes default security rules as specified in Agent-Security-Manager.md.
     */
    public void loadDefaultRules() {
        rules.clear();

        // file-access
        rules.add(new SecurityRule("file-access", "${WORKSPACE_DIR}/docs/secrets/*", true));
        rules.add(new SecurityRule("file-access", "${WORKSPACE_DIR}/docs/*", false));

        // program-execution
        rules.add(new SecurityRule("program-execution", "ls", false));
        rules.add(new SecurityRule("program-execution", "find", false));
        rules.add(new SecurityRule("program-execution", "cat", false));
        rules.add(new SecurityRule("program-execution", "grep", false));
        rules.add(new SecurityRule("program-execution", "pwd", false));
        rules.add(new SecurityRule("program-execution", "rm", true));
        rules.add(new SecurityRule("program-execution", "poweroff", true));
        rules.add(new SecurityRule("program-execution", "/usr/bin/*", false));
        rules.add(new SecurityRule("program-execution", "git", false));
        rules.add(new SecurityRule("program-execution", "mvn", false));

        // http-url
        rules.add(new SecurityRule("http-url", "https://api.github.com/*", false));
        rules.add(new SecurityRule("http-url", "https://*.openai.com/*", false));
    }

    public File getActiveWorkspace() {
        return activeWorkspace;
    }

    public void setActiveWorkspace(final File workspace) {
        this.activeWorkspace = workspace;
        if (workspace != null) {
            final File confFile = new File(workspace, "security_rules.conf");
            if (confFile.exists()) {
                loadFromFile(confFile);
            } else {
                // Save default rules if .conf doesn't exist yet
                saveToFile(confFile);
            }
        }
    }

    /**
     * Helper to check if security restrictions are active.
     * Returns true if all rules are enabled, and false if any rule is disabled.
     */
    public boolean isEnabled() {
        if (rules.isEmpty()) {
            return true;
        }
        for (final SecurityRule rule : rules) {
            if (!rule.enabled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Enables or disables all security rules collectively.
     */
    public synchronized void setEnabled(final boolean enabled) {
        boolean changed = false;
        for (int i = 0; i < rules.size(); i++) {
            final SecurityRule r = rules.get(i);
            if (r.enabled() != enabled) {
                rules.set(i, new SecurityRule(r.category(), r.pattern(), r.isDeny(), enabled));
                changed = true;
            }
        }
        if (changed) {
            LOGGER.log(Level.INFO, "Security rules enabled status collectively set to: " + enabled);
            if (!enabled) {
                scheduleAutoRestoration();
            } else {
                cancelAutoRestoration();
            }
            triggerStateChanged();
        }
    }

    public int getAutoRestoreMinutes() {
        return autoRestoreMinutes;
    }

    public void setAutoRestoreMinutes(final int minutes) {
        this.autoRestoreMinutes = minutes;
        LOGGER.log(Level.INFO, "Auto restore minutes set to: " + minutes);
        boolean hasDisabled = false;
        for (final SecurityRule rule : rules) {
            if (!rule.enabled()) {
                hasDisabled = true;
                break;
            }
        }
        if (hasDisabled) {
            // Re-schedule based on new minutes
            scheduleAutoRestoration();
        }
    }

    public List<SecurityRule> getRules() {
        return List.copyOf(rules);
    }

    public void setRules(final List<SecurityRule> newRules) {
        this.rules.clear();
        this.rules.addAll(newRules);
        LOGGER.log(Level.INFO, "Security rules updated. Count: " + newRules.size());

        boolean hasDisabled = false;
        for (final SecurityRule rule : newRules) {
            if (!rule.enabled()) {
                hasDisabled = true;
                break;
            }
        }
        if (hasDisabled) {
            scheduleAutoRestoration();
        } else {
            cancelAutoRestoration();
        }
    }

    public List<AuditLogEntry> getAuditLogs() {
        return List.copyOf(auditLogs);
    }

    public void clearAuditLogs() {
        auditLogs.clear();
    }

    public void registerOnSecurityStateChanged(final Runnable callback) {
        this.onSecurityStateChangedCallback = callback;
    }

    public void registerOnAutoRestore(final Runnable callback) {
        this.onAutoRestoreCallback = callback;
    }

    private void triggerStateChanged() {
        if (onSecurityStateChangedCallback != null) {
            Platform.runLater(onSecurityStateChangedCallback);
        }
    }

    private synchronized void scheduleAutoRestoration() {
        cancelAutoRestoration();
        if (autoRestoreMinutes <= 0) {
            return;
        }

        LOGGER.log(Level.INFO, "Scheduling auto-restoration in " + autoRestoreMinutes + " minutes");
        restoreTaskFuture = scheduler.schedule(() -> {
            synchronized (SecurityService.this) {
                boolean changed = false;
                for (int i = 0; i < rules.size(); i++) {
                    final SecurityRule r = rules.get(i);
                    if (!r.enabled()) {
                        rules.set(i, new SecurityRule(r.category(), r.pattern(), r.isDeny(), true));
                        changed = true;
                    }
                }
                if (changed) {
                    LOGGER.log(Level.INFO, "Security automatically re-enabled by restoration timer.");
                    triggerStateChanged();
                    if (onAutoRestoreCallback != null) {
                        Platform.runLater(onAutoRestoreCallback);
                    }
                }
            }
        }, autoRestoreMinutes, TimeUnit.MINUTES);
    }

    private synchronized void cancelAutoRestoration() {
        if (restoreTaskFuture != null && !restoreTaskFuture.isDone()) {
            restoreTaskFuture.cancel(false);
            LOGGER.log(Level.INFO, "Canceled scheduled auto-restoration task.");
        }
    }

    /**
     * Evaluates whether the requested action is permitted under the specified category.
     *
     * @param category     "file-access", "program-execution", or "http-url"
     * @param action       the specific action path, command, or URL to evaluate
     * @param workspaceDir the active workspace directory absolute path (can be null or empty)
     * @return true if permitted, false if blocked
     */
    public boolean checkPermission(final String category, final String action, final String workspaceDir) {
        // If ALL rules are disabled, we bypass checks entirely and return true!
        boolean allDisabled = true;
        for (final SecurityRule rule : rules) {
            if (rule.enabled()) {
                allDisabled = false;
                break;
            }
        }
        if (!rules.isEmpty() && allDisabled) {
            logAudit(category, action, "ALLOW (Bypassed)");
            return true;
        }

        for (final SecurityChecker checker : checkers) {
            if (checker.supports(category)) {
                final boolean permitted = checker.checkPermission(category, action, workspaceDir);
                if (permitted) {
                    logAudit(category, action, "ALLOW");
                    return true;
                } else {
                    logAudit(category, action, "DENY (Block)");
                    return false;
                }
            }
        }

        // Default Deny
        logAudit(category, action, "DENY (Block/Default)");
        return false;
    }

    private void logAudit(final String category, final String operation, final String result) {
        final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        final AuditLogEntry entry = new AuditLogEntry(timestamp, category, operation, result);
        auditLogs.add(0, entry); // Insert at the beginning to show latest first
        LOGGER.log(Level.INFO, String.format("Audit Log - [%s] Category: %s, Operation: %s, Result: %s", timestamp, category, operation, result));
    }

    /**
     * Helper to match wildcard patterns.
     */
    public static boolean matchesWildcard(final String pattern, final String value) {
        if (pattern == null || value == null) {
            return false;
        }
        final StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if ("\\.[]{}()+-?^$|".indexOf(c) != -1) {
                sb.append("\\").append(c);
            } else {
                sb.append(c);
            }
        }
        sb.append("$");
        return value.matches(sb.toString());
    }

    private String normalizePath(final String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
    }

    /**
     * Loads rules from a configuration file.
     */
    public void loadFromFile(final File file) {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            LOGGER.log(Level.INFO, "Loading security rules from file: " + file.getAbsolutePath());
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<SecurityRule> parsedRules = new ArrayList<>();
            String currentCategory = null;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    if (line.startsWith("#inactive#") && currentCategory != null) {
                        // Continue to parse it
                    } else {
                        continue;
                    }
                }

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentCategory = line.substring(1, line.length() - 1).trim();
                    continue;
                }

                if (currentCategory != null) {
                    boolean enabled = true;
                    String cleanLine = line;
                    if (cleanLine.startsWith("#inactive#")) {
                        enabled = false;
                        cleanLine = cleanLine.substring("#inactive#".length()).trim();
                    }
                    boolean isDeny = false;
                    String pattern = cleanLine;
                    if (cleanLine.startsWith("!")) {
                        isDeny = true;
                        pattern = cleanLine.substring(1).trim();
                    }
                    parsedRules.add(new SecurityRule(currentCategory, pattern, isDeny, enabled));
                }
            }

            setRules(parsedRules);
            triggerStateChanged();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load security rules from file: " + file.getAbsolutePath(), e);
            throw new UncheckedIOException("Failed to load security rules: " + file.getName(), e);
        }
    }

    /**
     * Saves rules to a configuration file in .conf format.
     */
    public void saveToFile(final File file) {
        if (file == null) {
            return;
        }

        try {
            LOGGER.log(Level.INFO, "Saving security rules to file: " + file.getAbsolutePath());
            final List<String> lines = new ArrayList<>();

            // Write categories
            final String[] categories = {"file-access", "program-execution", "http-url"};
            for (final String category : categories) {
                lines.add("[" + category + "]");
                for (final SecurityRule rule : rules) {
                    if (rule.category().equalsIgnoreCase(category)) {
                        String line = (rule.enabled() ? "" : "#inactive#") + rule.toLine();
                        lines.add(line);
                    }
                }
                lines.add(""); // Empty line between sections
            }

            // Ensure parent directory exists
            final File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save security rules to file: " + file.getAbsolutePath(), e);
            throw new UncheckedIOException("Failed to save security rules: " + file.getName(), e);
        }
    }
}
