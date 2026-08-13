package net.hero.genai.service;

import java.io.File;

/**
 * Support AI capability to check if a specific file or directory exists and retrieve its metadata.
 * Expected argument is a relative or absolute file path.
 */
public final class FileLookupCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "file-lookup";
    }

    @Override
    public String execute(final String argument) {
        if (argument == null || argument.isBlank()) {
            return "NOT_FOUND";
        }
        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "NO_ACTIVE_WORKSPACE";
        }

        final File file = new File(workspaceDir, argument);
        if (!file.exists()) {
            return "NOT_FOUND";
        }
        if (file.isDirectory()) {
            return "DIRECTORY";
        }
        return "EXISTS";
    }
}
