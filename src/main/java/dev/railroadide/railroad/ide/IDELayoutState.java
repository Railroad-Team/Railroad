package dev.railroadide.railroad.ide;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serializable IDE workspace state, split into independent snapshots for each registered workspace mode.
 * <p>
 * Selected tab IDs, per-mode navigation histories, and detached tool windows are restored on a best-effort basis.
 * Tabs which no longer exist are ignored, and layouts saved before tool-window persistence was introduced keep the
 * default docked placement.
 *
 * @param schemaVersion version of the persisted layout format
 * @param currentModeId identifier of the selected workspace mode
 * @param modes saved layouts keyed by workspace mode
 * @param toolWindows detached tool window snapshots
 */
public record IDELayoutState(
    int schemaVersion,
    String currentModeId,
    Map<String, ModeLayout> modes,
    List<ToolWindowState> toolWindows
) {
    /**
     * Version written by the current workspace layout serializer.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Normalizes saved identifiers and copies layout and tool-window state.
     *
     * @param schemaVersion version of the persisted layout format
     * @param currentModeId identifier of the selected workspace mode
     * @param modes saved layouts keyed by workspace mode
     * @param toolWindows detached tool window snapshots
     */
    public IDELayoutState {
        currentModeId = normalizeModeId(currentModeId);
        if (currentModeId == null) {
            currentModeId = WorkspaceMode.defaultMode().getId();
        }

        var normalizedModes = new LinkedHashMap<String, ModeLayout>();
        if (modes != null) {
            modes.forEach((modeId, layout) -> {
                String normalizedModeId = normalizeModeId(modeId);
                if (normalizedModeId != null && layout != null) {
                    normalizedModes.put(normalizedModeId, layout);
                }
            });
        }
        modes = Collections.unmodifiableMap(normalizedModes);
        toolWindows = toolWindows == null
            ? List.of()
            : toolWindows.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Creates a snapshot in the current schema from registered workspace modes.
     *
     * @param currentMode selected mode, or null for the default
     * @param modes saved layouts keyed by workspace mode
     */
    public IDELayoutState(WorkspaceMode currentMode, Map<WorkspaceMode, ModeLayout> modes) {
        this(currentMode, modes, List.of());
    }

    /**
     * Creates a snapshot in the current schema from registered workspace modes.
     *
     * @param currentMode selected mode, or null for the default
     * @param modes saved layouts keyed by workspace mode
     * @param toolWindows detached tool window snapshots
     */
    public IDELayoutState(
        WorkspaceMode currentMode,
        Map<WorkspaceMode, ModeLayout> modes,
        List<ToolWindowState> toolWindows
    ) {
        this(
            CURRENT_SCHEMA_VERSION,
            resolve(currentMode).getId(),
            toPersistedModes(modes),
            toolWindows);
    }

    /**
     * Checks whether this snapshot uses the current layout schema.
     *
     * @return whether the schema version is supported
     */
    public boolean isSupported() {
        return schemaVersion == CURRENT_SCHEMA_VERSION;
    }

    /**
     * Resolves the saved mode identifier, falling back to the default mode.
     *
     * @return registered workspace mode
     */
    public WorkspaceMode currentMode() {
        return WorkspaceMode.fromId(currentModeId).orElseGet(WorkspaceMode::defaultMode);
    }

    /**
     * Resolves saved layouts for modes that are currently registered.
     *
     * @return immutable layouts keyed by registered mode
     */
    public Map<WorkspaceMode, ModeLayout> knownModeLayouts() {
        var knownModes = new LinkedHashMap<WorkspaceMode, ModeLayout>();
        modes.forEach((modeId, layout) -> WorkspaceMode.fromId(modeId)
            .ifPresent(mode -> knownModes.put(mode, layout)));
        return Collections.unmodifiableMap(knownModes);
    }

    private static Map<String, ModeLayout> toPersistedModes(Map<WorkspaceMode, ModeLayout> modes) {
        if (modes == null || modes.isEmpty())
            return Map.of();

        var persistedModes = new LinkedHashMap<String, ModeLayout>();
        modes.forEach((mode, layout) -> {
            if (mode != null && layout != null) {
                persistedModes.put(mode.getId(), layout);
            }
        });
        return persistedModes;
    }

    private static String normalizeModeId(String modeId) {
        if (modeId == null || modeId.isBlank())
            return null;

        String trimmedId = modeId.trim();
        return WorkspaceMode.fromId(trimmedId)
            .map(WorkspaceMode::getId)
            .orElse(trimmedId);
    }

    private static WorkspaceMode resolve(WorkspaceMode mode) {
        return mode == null ? WorkspaceMode.defaultMode() : mode;
    }

    /**
     * Persisted dock selections, divider positions, visibility, and editor history for one mode.
     *
     * @param selectedLeftTab selected left dock item identifier
     * @param selectedEditorTab selected editor tab identifier
     * @param selectedRightTab selected right dock item identifier
     * @param selectedBottomTab selected bottom dock item identifier
     * @param leftDividerPosition normalized left divider position
     * @param rightDividerPosition normalized right divider position
     * @param bottomDividerPosition normalized bottom divider position
     * @param leftDockVisible whether the left dock is visible
     * @param rightDockVisible whether the right dock is visible
     * @param bottomDockVisible whether the bottom dock is visible
     * @param editorNavigation editor tab history, or null for an empty history
     */
    public record ModeLayout(
        String selectedLeftTab,
        String selectedEditorTab,
        String selectedRightTab,
        String selectedBottomTab,
        double leftDividerPosition,
        double rightDividerPosition,
        double bottomDividerPosition,
        boolean leftDockVisible,
        boolean rightDockVisible,
        boolean bottomDockVisible,
        TabNavigationState editorNavigation
    ) {
        /**
         * Creates a mode layout, replacing invalid divider positions with defaults.
         *
         * @param selectedLeftTab selected left dock item identifier
         * @param selectedEditorTab selected editor tab identifier
         * @param selectedRightTab selected right dock item identifier
         * @param selectedBottomTab selected bottom dock item identifier
         * @param leftDividerPosition normalized left divider position
         * @param rightDividerPosition normalized right divider position
         * @param bottomDividerPosition normalized bottom divider position
         * @param leftDockVisible whether the left dock is visible
         * @param rightDockVisible whether the right dock is visible
         * @param bottomDockVisible whether the bottom dock is visible
         * @param editorNavigation editor tab history, or null for an empty history
         */
        public ModeLayout {
            leftDividerPosition = validDivider(leftDividerPosition, 0.15);
            rightDividerPosition = validDivider(rightDividerPosition, 0.85);
            bottomDividerPosition = validDivider(bottomDividerPosition, 0.75);
            editorNavigation = editorNavigation == null ? TabNavigationState.empty() : editorNavigation;
        }

        /**
         * Creates a mode layout, replacing invalid divider positions with defaults.
         *
         * @param selectedLeftTab selected left dock item identifier
         * @param selectedEditorTab selected editor tab identifier
         * @param selectedRightTab selected right dock item identifier
         * @param selectedBottomTab selected bottom dock item identifier
         * @param leftDividerPosition normalized left divider position
         * @param rightDividerPosition normalized right divider position
         * @param bottomDividerPosition normalized bottom divider position
         * @param leftDockVisible whether the left dock is visible
         * @param rightDockVisible whether the right dock is visible
         * @param bottomDockVisible whether the bottom dock is visible
         */
        public ModeLayout(
            String selectedLeftTab,
            String selectedEditorTab,
            String selectedRightTab,
            String selectedBottomTab,
            double leftDividerPosition,
            double rightDividerPosition,
            double bottomDividerPosition,
            boolean leftDockVisible,
            boolean rightDockVisible,
            boolean bottomDockVisible
        ) {
            this(
                selectedLeftTab,
                selectedEditorTab,
                selectedRightTab,
                selectedBottomTab,
                leftDividerPosition,
                rightDividerPosition,
                bottomDividerPosition,
                leftDockVisible,
                rightDockVisible,
                bottomDockVisible,
                TabNavigationState.empty());
        }

        /**
         * Creates a layout with visible docks, default divider positions, and no selected tabs.
         *
         * @return default mode layout
         */
        public static ModeLayout defaults() {
            return new ModeLayout(
                null,
                null,
                null,
                null,
                0.15,
                0.85,
                0.75,
                true,
                true,
                true,
                TabNavigationState.empty());
        }

        /**
         * Copies this layout with replacement editor navigation history.
         *
         * @param navigation replacement editor tab history
         * @return layout containing the supplied history
         */
        public ModeLayout withEditorNavigation(TabNavigationState navigation) {
            return new ModeLayout(
                selectedLeftTab,
                selectedEditorTab,
                selectedRightTab,
                selectedBottomTab,
                leftDividerPosition,
                rightDividerPosition,
                bottomDividerPosition,
                leftDockVisible,
                rightDockVisible,
                bottomDockVisible,
                navigation);
        }

        private static double validDivider(double value, double fallback) {
            return Double.isFinite(value) && value > 0.0 && value < 1.0 ? value : fallback;
        }
    }

    /**
     * Persisted tab navigation entries and the current cursor.
     *
     * @param entries visited tab identifiers in navigation order
     * @param currentIndex index of the current history entry
     */
    public record TabNavigationState(List<String> entries, int currentIndex) {
        /**
         * Normalizes tab identifiers and clamps the cursor to the retained entries.
         *
         * @param entries visited tab identifiers in navigation order
         * @param currentIndex index of the current history entry
         */
        public TabNavigationState {
            entries = entries == null
                ? List.of()
                : entries.stream()
                    .filter(entry -> entry != null && !entry.isBlank())
                    .map(String::trim)
                    .toList();
            currentIndex = entries.isEmpty() ? -1 : Math.clamp(currentIndex, 0, entries.size() - 1);
        }

        /**
         * Creates navigation state with no entries and cursor {@code -1}.
         *
         * @return empty navigation state
         */
        public static TabNavigationState empty() {
            return new TabNavigationState(List.of(), -1);
        }
    }

    /**
     * Persisted tab arrangement and window geometry for a detached tool window.
     *
     * @param id stable identifier
     * @param dockItemIds identifiers of tabs in the detached window
     * @param selectedDockItemId identifier of the selected detached tab
     * @param x window horizontal screen position
     * @param y window vertical screen position
     * @param width window width
     * @param height window height
     * @param maximized whether the window is maximized
     * @param visible whether the window is visible
     */
    public record ToolWindowState(
        String id,
        List<String> dockItemIds,
        String selectedDockItemId,
        double x,
        double y,
        double width,
        double height,
        boolean maximized,
        boolean visible
    ) {
        /**
         * Normalizes detached tab identifiers, selection, and window geometry.
         *
         * @param id stable identifier
         * @param dockItemIds identifiers of tabs in the detached window
         * @param selectedDockItemId identifier of the selected detached tab
         * @param x window horizontal screen position
         * @param y window vertical screen position
         * @param width window width
         * @param height window height
         * @param maximized whether the window is maximized
         * @param visible whether the window is visible
         */
        public ToolWindowState {
            id = normalizeValue(id);
            dockItemIds = dockItemIds == null
                ? List.of()
                : dockItemIds.stream()
                    .map(ToolWindowState::normalizeValue)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            selectedDockItemId = normalizeValue(selectedDockItemId);
            if (!dockItemIds.contains(selectedDockItemId)) {
                selectedDockItemId = dockItemIds.isEmpty() ? null : dockItemIds.getFirst();
            }
            x = finiteOr(x, 100.0);
            y = finiteOr(y, 100.0);
            width = positiveOr(width, 400.0);
            height = positiveOr(height, 400.0);
        }

        private static String normalizeValue(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static double finiteOr(double value, double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }

        private static double positiveOr(double value, double fallback) {
            return Double.isFinite(value) && value > 0.0 ? value : fallback;
        }
    }
}
