package net.hero.genai.supportai.capability;

import net.hero.genai.security.SecurityService;

import java.io.File;
import java.util.Arrays;

/**
 * Support AI capability to list files and subdirectories in a directory relative to workspace root.
 * Argument is relative directory path (or empty for workspace root).
 */
public final class DirectoryListCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "directory-list";
    }

    @Override
    public String execute(final String argument) {
        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "NO_ACTIVE_WORKSPACE";
        }

        File targetDir = workspaceDir;
        if (argument != null && !argument.isBlank()) {
            targetDir = new File(workspaceDir, argument.trim());
        }

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return "DIRECTORY_NOT_FOUND";
        }

        final File[] files = targetDir.listFiles();
        if (files == null || files.length == 0) {
            return "Empty directory.";
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        final StringBuilder sb = new StringBuilder();
        for (final File file : files) {
            if (file.isDirectory()) {
                sb.append("[DIR]  ").append(file.getName()).append("/\n");
            } else {
                sb.append("[FILE] ").append(file.getName()).append(" (").append(file.length()).append(" bytes)\n");
            }
        }
        return sb.toString().trim();
    }
}
