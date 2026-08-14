package dev.railroadide.railroad.ide;

import java.util.Map;

/**
 * Serializable IDE workspace state, split into independent snapshots for each view mode.
 */
public record IDELayoutState(IDEViewMode currentViewMode, Map<IDEViewMode, ModeLayout> modes) {
    public IDELayoutState {
        currentViewMode = currentViewMode == null ? IDEViewMode.CODE : currentViewMode;
        modes = modes == null ? Map.of() : Map.copyOf(modes);
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
        public static ModeLayout defaults() {
            return new ModeLayout(null, null, null, null, 0.15, 0.85, 0.75, true, true, true);
        }
    }
}
