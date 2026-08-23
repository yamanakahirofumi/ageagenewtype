package net.hero.genai.supportai;

import net.hero.genai.git.GitService;
import net.hero.genai.git.GitStatus;
import net.hero.genai.security.SecurityService;

import java.io.File;

/**
 * Support AI capability to get current Git status.
 * Argument is optional (can specify a subpath or be null/empty for workspace root).
 */
public final class GitStatusCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "git-status";
    }

    @Override
    public String execute(final String argument) {
        final File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
        if (workspaceDir == null) {
            return "NO_ACTIVE_WORKSPACE";
        }

        File targetDir = workspaceDir;
        if (argument != null && !argument.isBlank()) {
            final File sub = new File(workspaceDir, argument.trim());
            if (sub.exists() && sub.isDirectory()) {
                targetDir = sub;
            }
        }

        final GitService gitService = new GitService();
        if (!gitService.isGitRepository(targetDir)) {
            return "NOT_A_GIT_REPOSITORY";
        }

        try {
            final GitStatus status = gitService.getStatus(targetDir);
            final StringBuilder sb = new StringBuilder();
            sb.append("Branch: ").append(status.branchName()).append("\n");
            sb.append("Ahead: ").append(status.aheadCount()).append(", Behind: ").append(status.behindCount()).append("\n");
            sb.append("Staged: ").append(status.staged()).append("\n");
            sb.append("Unstaged: ").append(status.unstaged()).append("\n");
            sb.append("Untracked: ").append(status.untracked()).append("\n");
            sb.append("Conflicting: ").append(status.conflicting());
            return sb.toString();
        } catch (Exception e) {
            return "Error retrieving Git status: " + e.getMessage();
        }
    }
}
