package net.hero.genai.supportai;

import net.hero.genai.security.SecurityService;

import java.io.File;

/**
 * Support AI capability to perform security checks on behalf of the agent.
 * Expected argument format: "<category>:<action>"
 * e.g., "file-access:/workspace/docs/secrets/credentials.txt"
 */
public final class SecurityCheckCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "security-check";
    }

    @Override
    public String execute(final String argument) {
        if (argument == null || !argument.contains(":")) {
            return "Error: Invalid argument format. Expected '<category>:<action>'";
        }
        final int firstColon = argument.indexOf(':');
        final String category = argument.substring(0, firstColon).trim();
        final String action = argument.substring(firstColon + 1).trim();

        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        final String workspacePath = workspaceDir != null ? workspaceDir.getAbsolutePath() : null;

        final boolean permitted = SecurityService.getInstance().checkPermission(category, action, workspacePath);
        return permitted ? "PERMITTED" : "BLOCKED";
    }
}
