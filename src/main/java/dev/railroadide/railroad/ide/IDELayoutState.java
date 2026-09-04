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
 */
public record IDELayoutState(
    int schemaVersion,
    String currentModeId,
    Map<String, ModeLayout> modes,
    List<ToolWindowState> toolWindows) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

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

    public IDELayoutState(WorkspaceMode currentMode, Map<WorkspaceMode, ModeLayout> modes) {
        this(currentMode, modes, List.of());
    }

    public IDELayoutState(
        WorkspaceMode currentMode,
        Map<WorkspaceMode, ModeLayout> modes,
        List<ToolWindowState> toolWindows) {
        this(
            CURRENT_SCHEMA_VERSION,
            resolve(currentMode).getId(),
            toPersistedModes(modes),
            toolWindows);
    }

    public boolean isSupported() {
        return schemaVersion == CURRENT_SCHEMA_VERSION;
    }

    public WorkspaceMode currentMode() {
        return WorkspaceMode.fromId(currentModeId).orElseGet(WorkspaceMode::defaultMode);
    }

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
        TabNavigationState editorNavigation) {
        public ModeLayout {
            leftDividerPosition = validDivider(leftDividerPosition, 0.15);
            rightDividerPosition = validDivider(rightDividerPosition, 0.85);
            bottomDividerPosition = validDivider(bottomDividerPosition, 0.75);
            editorNavigation = editorNavigation == null ? TabNavigationState.empty() : editorNavigation;
        }

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
            boolean bottomDockVisible) {
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

    public record TabNavigationState(List<String> entries, int currentIndex) {
        public TabNavigationState {
            entries = entries == null
                ? List.of()
                : entries.stream()
                    .filter(entry -> entry != null && !entry.isBlank())
                    .map(String::trim)
                    .toList();
            currentIndex = entries.isEmpty() ? -1 : Math.clamp(currentIndex, 0, entries.size() - 1);
        }

        public static TabNavigationState empty() {
            return new TabNavigationState(List.of(), -1);
        }
    }

    public record ToolWindowState(
        String id,
        List<String> dockItemIds,
        String selectedDockItemId,
        double x,
        double y,
        double width,
        double height,
        boolean maximized,
        boolean visible) {
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
