package net.hero.genai.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;

/**
 * Support AI capability to get recent Git commit log.
 * Argument can specify maximum number of commits (default 5).
 */
public final class GitLogCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "git-log";
    }

    @Override
    public String execute(final String argument) {
        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "NO_ACTIVE_WORKSPACE";
        }

        int maxCommits = 5;
        if (argument != null && !argument.isBlank()) {
            try {
                maxCommits = Math.max(1, Integer.parseInt(argument.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        final GitService gitService = new GitService();
        if (!gitService.isGitRepository(workspaceDir)) {
            return "NOT_A_GIT_REPOSITORY";
        }

        try {
            final FileRepositoryBuilder builder = new FileRepositoryBuilder()
                    .readEnvironment()
                    .findGitDir(workspaceDir);
            if (builder.getGitDir() == null) {
                return "NOT_A_GIT_REPOSITORY";
            }

            try (final Repository repository = builder.build();
                 final Git git = new Git(repository)) {
                final Iterable<RevCommit> commits = git.log().setMaxCount(maxCommits).call();
                final StringBuilder sb = new StringBuilder();
                int count = 0;
                for (final RevCommit commit : commits) {
                    if (count > 0) {
                        sb.append("\n");
                    }
                    sb.append("Commit: ").append(commit.getId().name().substring(0, 7))
                      .append(" | Author: ").append(commit.getAuthorIdent().getName())
                      .append(" | Message: ").append(commit.getShortMessage());
                    count++;
                }
                return count == 0 ? "No commits found." : sb.toString();
            }
        } catch (NoHeadException e) {
            return "No commits found.";
        } catch (Exception e) {
            return "Error retrieving Git log: " + e.getMessage();
        }
    }
}
