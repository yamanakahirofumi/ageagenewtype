package net.hero.genai.supportai;

import net.hero.genai.git.GitService;
import net.hero.genai.security.SecurityService;

import java.io.File;

/**
 * Support AI capability to retrieve overview information about active workspace and environment status.
 */
public final class WorkspaceInfoCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "workspace-info";
    }

    @Override
    public String execute(final String argument) {
        final SecurityService securityService = SecurityService.getInstance();
        final File workspaceDir = securityService.getActiveWorkspace();

        final StringBuilder sb = new StringBuilder();
        sb.append("Workspace Active: ").append(workspaceDir != null).append("\n");
        if (workspaceDir != null) {
            sb.append("Workspace Path: ").append(workspaceDir.getAbsolutePath()).append("\n");
            final GitService gitService = new GitService();
            sb.append("Git Repository: ").append(gitService.isGitRepository(workspaceDir)).append("\n");
        }
        sb.append("Security Enforcement Enabled: ").append(securityService.isEnabled()).append("\n");
        sb.append("Security Rules Count: ").append(securityService.getRules().size());
        return sb.toString();
    }
}
