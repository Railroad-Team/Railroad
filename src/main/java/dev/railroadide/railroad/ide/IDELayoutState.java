package dev.railroadide.railroad.ide;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable IDE workspace state, split into independent snapshots for each registered workspace mode.
 * <p>
 * Only the current schema is accepted; this development format intentionally has no backwards-compatibility path.
 * Selected tab IDs are restored on a best-effort basis: tabs which no longer exist are ignored, and dynamically
 * generated Git editor tabs are intentionally session-only.
 */
public record IDELayoutState(
    int schemaVersion,
    String currentModeId,
    Map<String, ModeLayout> modes
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public IDELayoutState {
        currentModeId = normalizeModeId(currentModeId);
        if (currentModeId == null)
            currentModeId = WorkspaceMode.defaultMode().getId();

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
    }

    public IDELayoutState(WorkspaceMode currentMode, Map<WorkspaceMode, ModeLayout> modes) {
        this(
            CURRENT_SCHEMA_VERSION,
            resolve(currentMode).getId(),
            toPersistedModes(modes)
        );
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
        boolean bottomDockVisible
    ) {
        public ModeLayout {
            leftDividerPosition = validDivider(leftDividerPosition, 0.15);
            rightDividerPosition = validDivider(rightDividerPosition, 0.85);
            bottomDividerPosition = validDivider(bottomDividerPosition, 0.75);
        }

        public static ModeLayout defaults() {
            return new ModeLayout(null, null, null, null, 0.15, 0.85, 0.75, true, true, true);
        }

        private static double validDivider(double value, double fallback) {
            return Double.isFinite(value) && value > 0.0 && value < 1.0 ? value : fallback;
        }
    }
}
