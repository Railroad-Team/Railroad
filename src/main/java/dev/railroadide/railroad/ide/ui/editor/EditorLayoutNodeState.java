package dev.railroadide.railroad.ide.ui.editor;

import javafx.geometry.Orientation;

import java.util.List;
import java.util.Objects;

/**
 * Stores either an editor group leaf or a split containing child layout nodes.
 *
 * @param groupId editor group identifier, or null for a split
 * @param selectedDocumentId selected document identifier, or null when none is selected
 * @param orientation direction in which child panes are arranged
 * @param dividerPositions relative split-divider positions
 * @param children child layout nodes for a split
 */
public record EditorLayoutNodeState(
    String groupId,
    String selectedDocumentId,
    Orientation orientation,
    List<Double> dividerPositions,
    List<EditorLayoutNodeState> children
) {

    /**
     * Creates a layout node, normalizing identifiers and dividers and validating its group or split shape.
     *
     * @param groupId editor group identifier, or null for a split
     * @param selectedDocumentId selected document identifier, or null when none is selected
     * @param orientation direction in which child panes are arranged
     * @param dividerPositions relative split-divider positions
     * @param children child layout nodes for a split
     */
    public EditorLayoutNodeState {
        groupId = normalize(groupId);
        selectedDocumentId = normalize(selectedDocumentId);
        dividerPositions = dividerPositions == null
            ? List.of()
            : dividerPositions.stream()
                .filter(Objects::nonNull)
                .map(EditorLayoutNodeState::normalizeDivider)
                .toList();
        children = children == null
            ? List.of()
            : children.stream()
                .filter(Objects::nonNull)
                .toList();

        boolean leaf = groupId != null;
        if (leaf == !children.isEmpty())
            throw new IllegalArgumentException("An editor layout node must be exactly one of a group or split");
        if (!leaf && orientation == null) {
            orientation = Orientation.HORIZONTAL;
        }
    }

    /**
     * Creates a leaf for an editor group, supplying the default identifier when blank.
     *
     * @param groupId editor group identifier, or null for a split
     * @param selectedDocumentId selected document identifier, or null when none is selected
     * @return editor group layout node
     */
    public static EditorLayoutNodeState group(String groupId, String selectedDocumentId) {
        String resolvedGroupId = normalize(groupId);
        if (resolvedGroupId == null) {
            resolvedGroupId = EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID;
        }
        return new EditorLayoutNodeState(resolvedGroupId, selectedDocumentId, null, List.of(), List.of());
    }

    /**
     * Creates a split node containing the supplied child layouts.
     *
     * @param orientation direction in which child panes are arranged
     * @param dividerPositions relative split-divider positions
     * @param children child layout nodes for a split
     * @return split layout node
     */
    public static EditorLayoutNodeState split(
        Orientation orientation,
        List<Double> dividerPositions,
        List<EditorLayoutNodeState> children
    ) {
        return new EditorLayoutNodeState(null, null, orientation, dividerPositions, children);
    }

    /**
     * Reports whether this node represents an editor group.
     *
     * @return true for a group leaf and false for a split
     */
    public boolean group() {
        return groupId != null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank())
            return null;
        return value.trim();
    }

    private static double normalizeDivider(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.01, 0.99) : 0.5;
    }
}
