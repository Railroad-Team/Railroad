package dev.railroadide.railroad.ide.ui.editor;

import javafx.geometry.Orientation;

import java.util.List;
import java.util.Objects;

public record EditorLayoutNodeState(
    String groupId,
    String selectedDocumentId,
    Orientation orientation,
    List<Double> dividerPositions,
    List<EditorLayoutNodeState> children) {

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

    public static EditorLayoutNodeState group(String groupId, String selectedDocumentId) {
        String resolvedGroupId = normalize(groupId);
        if (resolvedGroupId == null) {
            resolvedGroupId = EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID;
        }
        return new EditorLayoutNodeState(resolvedGroupId, selectedDocumentId, null, List.of(), List.of());
    }

    public static EditorLayoutNodeState split(
        Orientation orientation,
        List<Double> dividerPositions,
        List<EditorLayoutNodeState> children) {
        return new EditorLayoutNodeState(null, null, orientation, dividerPositions, children);
    }

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
