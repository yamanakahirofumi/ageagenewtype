package net.hero.genai.workspace;

import java.io.File;

public final class WorkspaceFile {
    private final File file;
    private final String name;
    private final boolean isDirectory;

    public WorkspaceFile(final File file) {
        this.file = file;
        this.name = file.getName();
        this.isDirectory = file.isDirectory();
    }

    public File getFile() {
        return this.file;
    }

    public String getName() {
        return this.name;
    }

    public boolean isDirectory() {
        return this.isDirectory;
    }

    @Override
    public String toString() {
        return this.name.isEmpty() ? this.file.getAbsolutePath() : this.name;
    }
}
