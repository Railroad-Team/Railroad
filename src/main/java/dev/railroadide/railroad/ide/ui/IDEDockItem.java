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
 * <p>
 * Dock-item identity and presentation are deliberately independent of the localized tab title. This allows titles
 * to change without breaking layout restoration, icon lookup, or future routing.
 * </p>
 */
public enum IDEDockItem {
    /**
     * Project file explorer in the code workspace.
     */
    PROJECT(
        "dock-item:project",
        "railroad.ide.dock_item.project",
        FontAwesomeSolid.FOLDER,
        DockPosition.LEFT,
        WorkspaceModes.CODE,
        InitializationPolicy.EAGER,
        ProjectExplorerPane::new),
    /**
     * Repository overview tool in the Git workspace.
     */
    GIT_OVERVIEW(
        "dock-item:git-overview",
        "railroad.ide.dock_item.git_overview",
        FontAwesomeSolid.HOME,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitOverviewPane::new),
    /**
     * Change selection and commit composition tool.
     */
    GIT_COMMIT(
        "dock-item:git-commit",
        "railroad.ide.dock_item.git_commit",
        FontAwesomeBrands.USB,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitCommitPane::new),
    /**
     * Repository commit history tool.
     */
    GIT_COMMIT_LIST(
        "dock-item:git-commit-list",
        "railroad.ide.dock_item.git_commit_list",
        FontAwesomeSolid.LIST,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitCommitListPane::new),
    /**
     * Local and remote branch management tool.
     */
    GIT_BRANCHES(
        "dock-item:git-branches",
        "railroad.ide.dock_item.git_branches",
        FontAwesomeSolid.CODE_BRANCH,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitBranchesPane::new),
    /**
     * Remote repository configuration tool.
     */
    GIT_REMOTES(
        "dock-item:git-remotes",
        "railroad.ide.dock_item.git_remotes",
        FontAwesomeSolid.GLOBE,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitRemotesPane::new),
    /**
     * Incoming and outgoing commit synchronization tool.
     */
    GIT_SYNC(
        "dock-item:git-sync",
        "railroad.ide.dock_item.git_sync",
        FontAwesomeSolid.SYNC,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitSyncPane::new),
    /**
     * Saved working-tree stash management tool.
     */
    GIT_STASH(
        "dock-item:git-stash",
        "railroad.ide.dock_item.git_stash",
        FontAwesomeSolid.BOX,
        DockPosition.LEFT,
        WorkspaceModes.GIT,
        InitializationPolicy.ON_FIRST_SELECTION,
        GitStashPane::new),
    /**
     * Gradle task and build tools shared between workspace modes.
     */
    GRADLE(
        "dock-item:gradle",
        "railroad.ide.dock_item.gradle",
        RailroadBrandsIcon.GRADLE,
        DockPosition.RIGHT,
        null,
        InitializationPolicy.EAGER,
        GradleToolsPane::new),
    /**
     * Console output pane shared between workspace modes.
     */
    CONSOLE(
        "dock-item:console",
        "railroad.ide.dock_item.console",
        FontAwesomeSolid.PLAY_CIRCLE,
        DockPosition.BOTTOM,
        null,
        InitializationPolicy.EAGER,
        _ -> new ConsolePane()),
    /**
     * Terminal pane created on first selection.
     */
    TERMINAL(
        "dock-item:terminal",
        "railroad.ide.dock_item.terminal",
        FontAwesomeSolid.TERMINAL,
        DockPosition.BOTTOM,
        null,
        InitializationPolicy.ON_FIRST_SELECTION,
        project -> TerminalFactory.create(project.getPath()));

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
        this.preferredDockPosition = Objects.requireNonNull(preferredDockPosition,
            "Preferred dock position cannot be null");
        this.owningMode = owningMode;
        this.initializationPolicy = Objects.requireNonNull(initializationPolicy,
            "Initialization policy cannot be null");
        this.contentFactory = Objects.requireNonNull(contentFactory, "Content factory cannot be null");
    }

    /**
     * Returns the stable identifier used for persisted dock state.
     *
     * @return dock item identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the translation key for this tool's title.
     *
     * @return title localization key
     */
    public String localizationKey() {
        return localizationKey;
    }

    /**
     * Returns the icon used to represent this tool.
     *
     * @return tool icon
     */
    public Ikon icon() {
        return icon;
    }

    /**
     * Returns the tool's default dock edge.
     *
     * @return preferred workspace edge
     */
    public DockPosition preferredDockPosition() {
        return preferredDockPosition;
    }

    /**
     * Returns the mode that owns this item, or {@code null} when the item is shared between modes.
     *
     * @return owning mode, or null for a tool shared between modes
     */
    public @Nullable WorkspaceMode owningMode() {
        return owningMode;
    }

    /**
     * Returns when this tool's content should be created.
     *
     * @return content initialization policy
     */
    public InitializationPolicy initializationPolicy() {
        return initializationPolicy;
    }

    /**
     * Creates this tool's content for a project.
     *
     * @param project project whose files and workspace are being displayed
     * @return new tool content node
     */
    public Node createContent(Project project) {
        return contentFactory.apply(Objects.requireNonNull(project, "Project cannot be null"));
    }

    /**
     * Identifies the default edge of the editor workspace used by a tool pane.
     */
    public enum DockPosition {
        /**
         * Tool pane positioned to the left of the editor.
         */
        LEFT,
        /**
         * Tool pane positioned to the right of the editor.
         */
        RIGHT,
        /**
         * Tool pane positioned below the editor.
         */
        BOTTOM
    }

    /**
     * Controls when a tool pane creates its content.
     */
    public enum InitializationPolicy {
        /**
         * Create the tool content when the dock tab is created.
         */
        EAGER,
        /**
         * Defer tool content creation until the tab is first selected.
         */
        ON_FIRST_SELECTION
    }
}
