package net.hero.genai.git;

import java.util.List;

/**
 * Represents the current Git status of the workspace repository.
 */
public record GitStatus(
    String branchName,
    List<String> staged,
    List<String> unstaged,
    List<String> untracked,
    List<String> conflicting,
    int aheadCount,
    int behindCount
) {
    public GitStatus {
        staged = List.copyOf(staged);
        unstaged = List.copyOf(unstaged);
        untracked = List.copyOf(untracked);
        conflicting = List.copyOf(conflicting);
    }
}
