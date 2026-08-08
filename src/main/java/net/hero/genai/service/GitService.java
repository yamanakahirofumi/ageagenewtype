package net.hero.genai.service;

import net.hero.genai.model.GitStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service to handle Git operations using JGit library.
 */
public final class GitService {

    private static final Logger LOGGER = Logger.getLogger(GitService.class.getName());

    /**
     * Finds if a .git directory exists for the given workspace or any parent.
     */
    public boolean isGitRepository(final File directory) {
        if (directory == null || !directory.exists()) {
            return false;
        }
        try {
            final FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(directory);
            return builder.getGitDir() != null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking git repository at: " + directory.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Initializes a Git repository in the given directory.
     */
    public void init(final File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory for Git init");
        }
        LOGGER.log(Level.INFO, "Initializing Git repository at " + directory.getAbsolutePath());
        try (final Git git = Git.init().setDirectory(directory).call()) {
            LOGGER.log(Level.INFO, "Initialized empty Git repository in " + git.getRepository().getDirectory());
        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize git repository", e);
            throw new RuntimeException("Failed to initialize git repository", e);
        }
    }

    /**
     * Retrieves the current GitStatus of the repository.
     */
    public GitStatus getStatus(final File directory) {
        if (!isGitRepository(directory)) {
            return new GitStatus("No Git Repo", List.of(), List.of(), List.of(), List.of(), 0, 0);
        }

        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {

            final Status status = git.status().call();
            final String branch = repository.getBranch();

            final List<String> staged = new ArrayList<>();
            final List<String> unstaged = new ArrayList<>();
            final List<String> untracked = new ArrayList<>();
            final List<String> conflicting = new ArrayList<>();

            // JGit status classification
            staged.addAll(status.getAdded());
            staged.addAll(status.getChanged());
            staged.addAll(status.getRemoved());

            unstaged.addAll(status.getModified());
            unstaged.addAll(status.getMissing());

            untracked.addAll(status.getUntracked());
            untracked.addAll(status.getUntrackedFolders());

            conflicting.addAll(status.getConflicting());

            // Simple ahead / behind calculation can be integrated if upstream is configured, but let's mock it for local-only simplicity or use standard branch tracking status if possible
            int ahead = 0;
            int behind = 0;
            try {
                org.eclipse.jgit.lib.BranchTrackingStatus trackingStatus = org.eclipse.jgit.lib.BranchTrackingStatus.of(repository, branch);
                if (trackingStatus != null) {
                    ahead = trackingStatus.getAheadCount();
                    behind = trackingStatus.getBehindCount();
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to get branch tracking status", e);
            }

            return new GitStatus(branch, staged, unstaged, untracked, conflicting, ahead, behind);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read git repository status", e);
            throw new UncheckedIOException("Failed to read git repository status", e);
        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Failed to get git status", e);
            throw new RuntimeException("Failed to get git status", e);
        }
    }

    /**
     * Stages (add) a file to the index.
     */
    public void stageFile(final File directory, final String relativePath) {
        LOGGER.log(Level.INFO, "Staging file: " + relativePath);
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.add().addFilepattern(relativePath).call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stage file: " + relativePath, e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to stage file: " + relativePath, e);
        }
    }

    /**
     * Unstages (reset) a file.
     */
    public void unstageFile(final File directory, final String relativePath) {
        LOGGER.log(Level.INFO, "Unstaging file: " + relativePath);
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.reset().addPath(relativePath).call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to unstage file: " + relativePath, e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to unstage file: " + relativePath, e);
        }
    }

    /**
     * Commits staged changes with a message.
     */
    public void commit(final File directory, final String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Commit message cannot be empty");
        }
        LOGGER.log(Level.INFO, "Committing changes with message: " + message);
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.commit().setMessage(message).call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to commit changes", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to commit changes", e);
        }
    }

    /**
     * Fetches changes from remote.
     */
    public void fetch(final File directory) {
        LOGGER.log(Level.INFO, "Fetching remote updates...");
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.fetch().call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to fetch", e);
        }
    }

    /**
     * Pulls changes from remote.
     */
    public void pull(final File directory) {
        LOGGER.log(Level.INFO, "Pulling remote updates...");
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.pull().call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to pull", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to pull", e);
        }
    }

    /**
     * Pushes changes to remote.
     */
    public void push(final File directory) {
        LOGGER.log(Level.INFO, "Pushing local commits...");
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.push().call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to push", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to push", e);
        }
    }

    /**
     * Checkouts to a specific branch.
     */
    public void checkout(final File directory, final String branchName) {
        LOGGER.log(Level.INFO, "Checking out branch: " + branchName);
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.checkout().setName(branchName).call();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to checkout: " + branchName, e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to checkout: " + branchName, e);
        }
    }

    /**
     * Creates a new branch and checks it out if required.
     */
    public void createBranch(final File directory, final String branchName, final boolean checkout) {
        LOGGER.log(Level.INFO, "Creating branch: " + branchName + " (checkout: " + checkout + ")");
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            git.branchCreate().setName(branchName).call();
            if (checkout) {
                git.checkout().setName(branchName).call();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create branch: " + branchName, e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to create branch: " + branchName, e);
        }
    }

    /**
     * Helper to list local branches.
     */
    public List<String> listLocalBranches(final File directory) {
        if (!isGitRepository(directory)) {
            return Collections.emptyList();
        }
        try (final Repository repository = openRepository(directory);
             final Git git = new Git(repository)) {
            final List<org.eclipse.jgit.lib.Ref> call = git.branchList().call();
            return call.stream()
                .map(org.eclipse.jgit.lib.Ref::getName)
                .map(name -> Repository.shortenRefName(name))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list branches", e);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to list branches", e);
        }
    }

    private Repository openRepository(final File directory) throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder()
            .readEnvironment()
            .findGitDir(directory);
        if (builder.getGitDir() == null) {
            throw new IOException("Not a Git repository: " + directory.getAbsolutePath());
        }
        return builder.build();
    }
}
