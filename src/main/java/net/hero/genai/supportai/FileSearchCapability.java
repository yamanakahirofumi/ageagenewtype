package net.hero.genai.supportai;

import net.hero.genai.security.SecurityService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Support AI capability to search files in the active workspace by keyword or pattern in filename.
 * Argument is search query string.
 */
public final class FileSearchCapability implements SupportAICapability {

    private static final int MAX_RESULTS = 50;

    @Override
    public String getId() {
        return "file-search";
    }

    @Override
    public String execute(final String argument) {
        if (argument == null || argument.isBlank()) {
            return "Error: Search query is required.";
        }

        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "NO_ACTIVE_WORKSPACE";
        }

        final String query = argument.trim().toLowerCase();
        final List<String> matches = new ArrayList<>();

        try (final Stream<Path> stream = Files.walk(workspaceDir.toPath())) {
            stream.filter(Files::isRegularFile)
                  .filter(path -> path.getFileName().toString().toLowerCase().contains(query))
                  .forEach(path -> {
                      if (matches.size() < MAX_RESULTS) {
                          matches.add(workspaceDir.toPath().relativize(path).toString());
                      }
                  });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (matches.isEmpty()) {
            return "NO_MATCHES_FOUND";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matches.size()).append(" match(es):\n");
        for (final String match : matches) {
            sb.append("- ").append(match).append("\n");
        }
        return sb.toString().trim();
    }
}
