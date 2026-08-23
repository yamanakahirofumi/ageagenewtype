package net.hero.genai.workspace;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorkspaceFileService {

    private static final Logger LOGGER = Logger.getLogger(WorkspaceFileService.class.getName());

    /**
     * Lists child files/directories for a given directory, returning empty list if not a directory or doesn't exist.
     */
    public List<WorkspaceFile> listFiles(final File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return Collections.emptyList();
        }

        final File[] files = directory.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }

        final List<WorkspaceFile> list = new ArrayList<>();
        for (final File f : files) {
            list.add(new WorkspaceFile(f));
        }
        // Sort directories first, then files alphabetically
        list.sort((f1, f2) -> {
            if (f1.isDirectory() != f2.isDirectory()) {
                return f1.isDirectory() ? -1 : 1;
            }
            return f1.getName().compareToIgnoreCase(f2.getName());
        });
        return List.copyOf(list);
    }

    /**
     * Safely reads the text content of a file.
     * Wraps IOException in UncheckedIOException as per project Coding-Convention.md
     */
    public String readFileContent(final File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }
        try {
            LOGGER.log(Level.INFO, "Reading file content: " + file.getAbsolutePath());
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read file: " + file.getAbsolutePath(), e);
            throw new UncheckedIOException("Failed to read file: " + file.getName(), e);
        }
    }

    /**
     * Safely writes the text content to a file.
     * Wraps IOException in UncheckedIOException.
     */
    public void writeFileContent(final File file, final String content) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        try {
            LOGGER.log(Level.INFO, "Writing file content: " + file.getAbsolutePath());
            // Ensure parent directory exists
            final File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write file: " + file.getAbsolutePath(), e);
            throw new UncheckedIOException("Failed to write file: " + file.getName(), e);
        }
    }
}
