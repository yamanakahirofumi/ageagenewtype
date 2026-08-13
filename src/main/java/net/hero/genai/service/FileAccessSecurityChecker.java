package net.hero.genai.service;

/**
 * Security checker specializing in "file-access" verification.
 * Normalizes file paths and resolves the ${WORKSPACE_DIR} placeholder.
 */
public final class FileAccessSecurityChecker extends AbstractRuleSecurityChecker {

    public FileAccessSecurityChecker() {
        super("file-access");
    }

    @Override
    protected String preprocessAction(final String action, final String contextValue) {
        return normalizePath(action);
    }

    @Override
    protected String resolvePattern(final String pattern, final String contextValue) {
        String resolvedPattern = pattern;
        if (resolvedPattern.contains("${WORKSPACE_DIR}")) {
            final String resolvedWorkspace = contextValue != null ? normalizePath(contextValue) : "";
            resolvedPattern = resolvedPattern.replace("${WORKSPACE_DIR}", resolvedWorkspace);
        }
        return normalizePath(resolvedPattern);
    }

    private String normalizePath(final String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
    }
}
