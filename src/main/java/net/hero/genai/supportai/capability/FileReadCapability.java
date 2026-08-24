package net.hero.genai.supportai.capability;

import net.hero.genai.security.SecurityService;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Support AI capability to read text content from a workspace file safely with security checking.
 * Argument is relative file path in workspace. Optional line limit can be specified via format "path:maxLines".
 */
public final class FileReadCapability implements SupportAICapability {

    private static final int DEFAULT_MAX_LINES = 100;

    @Override
    public String getId() {
        return "file-read";
    }

    @Override
    public String execute(final String argument) {
        if (argument == null || argument.isBlank()) {
            return "Error: File path argument is required.";
        }

        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "NO_ACTIVE_WORKSPACE";
        }

        String relativePath = argument.trim();
        int maxLines = DEFAULT_MAX_LINES;

        if (relativePath.contains(":")) {
            final int colonIdx = relativePath.lastIndexOf(':');
            final String numStr = relativePath.substring(colonIdx + 1).trim();
            try {
                maxLines = Integer.parseInt(numStr);
                relativePath = relativePath.substring(0, colonIdx).trim();
            } catch (NumberFormatException ignored) {
            }
        }

        final File targetFile = new File(workspaceDir, relativePath);
        final String absolutePath = targetFile.getAbsolutePath();

        final String workspacePath = workspaceDir.getAbsolutePath();
        if (!SecurityService.getInstance().checkPermission("file-access", absolutePath, workspacePath)) {
            return "BLOCKED_BY_SECURITY";
        }

        if (!targetFile.exists() || !targetFile.isFile()) {
            return "FILE_NOT_FOUND";
        }

        try {
            final List<String> lines = Files.readAllLines(targetFile.toPath());
            final StringBuilder sb = new StringBuilder();
            final int limit = Math.min(lines.size(), maxLines);
            for (int i = 0; i < limit; i++) {
                sb.append(lines.get(i)).append("\n");
            }
            if (lines.size() > maxLines) {
                sb.append("... [truncated ").append(lines.size() - maxLines).append(" more lines]");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
