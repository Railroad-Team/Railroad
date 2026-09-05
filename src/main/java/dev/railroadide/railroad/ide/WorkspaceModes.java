package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.vcs.git.GitRepositoryState;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/** Registers Railroad's built-in workspace modes. */
public final class WorkspaceModes {
    /**
     * Built-in code editing workspace mode.
     */
    public static final WorkspaceMode CODE = WorkspaceMode.register(
        "railroad:code",
        "railroad.ide.view_mode.code",
        FontAwesomeSolid.CODE,
        "railroad:view_mode_code",
        _ -> true,
        _ -> null);
    /**
     * Built-in Git workspace mode, available for an active repository.
     */
    public static final WorkspaceMode GIT = WorkspaceMode.register(
        "railroad:git",
        "railroad.ide.view_mode.git",
        FontAwesomeBrands.GIT_ALT,
        "railroad:view_mode_git",
        project -> project.getGitManager().isActive(),
        project -> project.getGitManager().repositoryStateProperty()
            .isNotEqualTo(GitRepositoryState.AVAILABLE));

    private WorkspaceModes() {
    }

    /**
     * Triggers class initialization and registration of the built-in modes.
     */
    public static void initialize() {
    }
}
