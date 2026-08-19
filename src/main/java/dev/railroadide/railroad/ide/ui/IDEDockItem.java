package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.gradle.ui.GradleToolsPane;
import dev.railroadide.railroad.ide.WorkspaceMode;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.ui.git.branches.GitBranchesPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitPane;
import dev.railroadide.railroad.ide.ui.git.commit.list.GitCommitListPane;
import dev.railroadide.railroad.ide.ui.git.overview.GitOverviewPane;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemotesPane;
import dev.railroadide.railroad.ide.ui.git.stash.GitStashPane;
import dev.railroadide.railroad.ide.ui.git.sync.GitSyncPane;
import dev.railroadide.railroad.ide.ui.setup.TerminalFactory;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.icon.RailroadBrandsIcon;
import javafx.scene.Node;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Stable descriptors for built-in IDE dock items.
 *
 * <p>Dock-item identity and presentation are deliberately independent of the localized tab title. This allows titles
 * to change without breaking layout restoration, icon lookup, or future routing.</p>
 */
public enum IDEDockItem {
    PROJECT(
        "dock-item:project",
        "railroad.ide.dock_item.project",
        FontAwesomeSolid.FOLDER,
        DockPosition.LEFT,
        WorkspaceModes.CODE,
        InitializationPolicy.EAGER,
        ProjectExplorerPane::new
    ),
    GIT_OVERVIEW(
        "dock-item:git-overview",
        "railroad.ide.dock_item.git_overview",
        FontAwesomeSolid.HOME,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitOverviewPane::new
    ),
    GIT_COMMIT(
        "dock-item:git-commit",
        "railroad.ide.dock_item.git_commit",
        FontAwesomeBrands.USB,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitCommitPane::new
    ),
    GIT_COMMIT_LIST(
        "dock-item:git-commit-list",
        "railroad.ide.dock_item.git_commit_list",
        FontAwesomeSolid.LIST,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitCommitListPane::new
    ),
    GIT_BRANCHES(
        "dock-item:git-branches",
        "railroad.ide.dock_item.git_branches",
        FontAwesomeSolid.CODE_BRANCH,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitBranchesPane::new
    ),
    GIT_REMOTES(
        "dock-item:git-remotes",
        "railroad.ide.dock_item.git_remotes",
        FontAwesomeSolid.GLOBE,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitRemotesPane::new
    ),
    GIT_SYNC(
        "dock-item:git-sync",
        "railroad.ide.dock_item.git_sync",
        FontAwesomeSolid.SYNC,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitSyncPane::new
    ),
    GIT_STASH(
        "dock-item:git-stash",
        "railroad.ide.dock_item.git_stash",
        FontAwesomeSolid.BOX,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitStashPane::new
    ),
    GRADLE(
        "dock-item:gradle",
        "railroad.ide.dock_item.gradle",
        RailroadBrandsIcon.GRADLE,
        DockPosition.RIGHT,
        null,
        InitializationPolicy.EAGER,
        GradleToolsPane::new
    ),
    CONSOLE(
        "dock-item:console",
        "railroad.ide.dock_item.console",
        FontAwesomeSolid.PLAY_CIRCLE,
        DockPosition.BOTTOM,
        null,
        InitializationPolicy.EAGER,
        _ -> new ConsolePane()
    ),
    TERMINAL(
        "dock-item:terminal",
        "railroad.ide.dock_item.terminal",
        FontAwesomeSolid.TERMINAL,
        DockPosition.BOTTOM,
        null,
        InitializationPolicy.ON_FIRST_SELECTION,
        project -> TerminalFactory.create(project.getPath())
    );

    private final String id;
    private final String localizationKey;
    private final Ikon icon;
    private final DockPosition preferredDockPosition;
    private final @Nullable WorkspaceMode owningMode;
    private final InitializationPolicy initializationPolicy;
    private final Function<Project, ? extends Node> contentFactory;

    IDEDockItem(
        String id,
        String localizationKey,
        Ikon icon,
        DockPosition preferredDockPosition,
        @Nullable WorkspaceMode owningMode,
        InitializationPolicy initializationPolicy,
        Function<Project, ? extends Node> contentFactory
    ) {
        this.id = Objects.requireNonNull(id, "Dock-item ID cannot be null");
        this.localizationKey = Objects.requireNonNull(localizationKey, "Localization key cannot be null");
        this.icon = Objects.requireNonNull(icon, "Dock-item icon cannot be null");
        this.preferredDockPosition = Objects.requireNonNull(preferredDockPosition, "Preferred dock position cannot be null");
        this.owningMode = owningMode;
        this.initializationPolicy = Objects.requireNonNull(initializationPolicy, "Initialization policy cannot be null");
        this.contentFactory = Objects.requireNonNull(contentFactory, "Content factory cannot be null");
    }

    public String id() {
        return id;
    }

    public String localizationKey() {
        return localizationKey;
    }

    public Ikon icon() {
        return icon;
    }

    public DockPosition preferredDockPosition() {
        return preferredDockPosition;
    }

    /** Returns the mode that owns this item, or {@code null} when the item is shared between modes. */
    public @Nullable WorkspaceMode owningMode() {
        return owningMode;
    }

    public InitializationPolicy initializationPolicy() {
        return initializationPolicy;
    }

    public Node createContent(Project project) {
        return contentFactory.apply(Objects.requireNonNull(project, "Project cannot be null"));
    }

    public enum DockPosition {
        LEFT,
        RIGHT,
        BOTTOM
    }

    public enum InitializationPolicy {
        EAGER,
        ON_FIRST_SELECTION
    }
}
